# AI Incident Assistant

A small Spring Boot service that helps on-call engineers triage production incidents of a payment platform.
Given a free-text incident description, it returns a machine-readable analysis: category, summary,
severity, and up to three root-cause hypotheses with concrete diagnostic steps.

The LLM (Anthropic Claude) is one controlled stage of a pipeline. It is not a black-box function: its
output is validated, auto-corrected, regenerated when needed, and replaced by a deterministic fallback
when the model is unavailable.

## Running it

Requirements: JDK 21. Maven is not required, because the project ships the Maven wrapper.

```bash
export ANTHROPIC_API_KEY=sk-ant-...
./mvnw spring-boot:run
```

On Windows, use `mvnw.cmd spring-boot:run` and `set ANTHROPIC_API_KEY=...`.

Send an incident:

```bash
curl -s http://localhost:8080/api/v1/incidents/analyze \
  -H "Content-Type: application/json" \
  -d '{"description": "Customers complain that card payments often fail. payment-service logs show many timeouts when calling PayGate, starting from 12:05 UTC. Other services look normal."}'
```

More requests, including one written in Russian, are in [examples/requests.http](examples/requests.http).
You can run them from IntelliJ IDEA or VS Code REST Client.

Without `ANTHROPIC_API_KEY` the service still starts and answers. Every response then comes from the
rule-based fallback and is marked with `"source": "fallback"`.

Run the tests (no API key or network needed):

```bash
./mvnw test
```

### Configuration

All settings come from environment variables ([application.yml](src/main/resources/application.yml)):

| Variable | Default | Purpose |
|---|---|---|
| `ANTHROPIC_API_KEY` | none | API key. If it is empty, the fallback answers every request. |
| `ANTHROPIC_MODEL` | `claude-opus-5-5` | Model id |
| `ANTHROPIC_EFFORT` | `low` | Reasoning effort (`low`, `medium`, `high`, `xhigh`, `max`). Classification works well at `low`. |
| `ANTHROPIC_BASE_URL` | `https://api.anthropic.com` | API endpoint, e.g. a proxy |
| `ANTHROPIC_SERVER_SIDE_FALLBACK` | `true` | Lets the API route a refused request to another model. |
| `AGENT_MAX_ATTEMPTS` | `3` | Maximum LLM generations per incident, including corrections |
| `PORT` | `8080` | HTTP port |

## API

`POST /api/v1/incidents/analyze` with body `{"description": "..."}` (1 to 8000 characters).
A blank or oversized description returns `400` as an RFC 7807 problem detail.

```json
{
  "category": "External payment provider issue",
  "summary": "PayGate is timing out since 12:05 UTC, so most card payments fail ...",
  "severity": "high",
  "hypotheses": [
    {
      "title": "Degradation or incident on the PayGate side",
      "reasoning": "Timeouts are observed only when calling PayGate ...",
      "next_steps": ["Check PayGate status page ...", "Compare error and latency metrics ..."]
    }
  ],
  "meta": {
    "source": "llm",
    "attempts": 1,
    "input_language": "en",
    "similar_incidents": ["INC-101"],
    "repairs": [],
    "errors": []
  }
}
```

The four top-level fields follow the contract from the assignment. `meta` adds observability: whether
the answer came from the LLM or the fallback, how many generations it took, which past incidents were
retrieved, which auto-corrections were applied, and why earlier attempts were rejected.

## How the agent works

```
description
   │
   ▼
1. Parse (IncidentParser, no LLM)
   language, mentioned services and external dependencies, HTTP codes, times, endpoints, keywords
   │
   ▼
2. Retrieve context (ContextRetriever, KnowledgeBase)
   candidate categories by signal keywords, top-2 similar past incidents by keyword overlap
   │
   ▼
3. Generate (PromptBuilder → LlmClient)
   system prompt: platform description, allowed categories, severity scale, JSON Schema, rules
   user message: incident, extracted facts, candidates, similar incidents
   │
   ▼
4. Validate (ResponseValidator) ──invalid──► correction message with the exact errors ──► back to 3
   │ valid                                   (up to AGENT_MAX_ATTEMPTS)
   ▼                                          │ attempts exhausted or the LLM call failed
AnalysisResult  ◄──────────────── 5. Fallback (FallbackAnalyzer): rule-based answer
```

[IncidentAgent](src/main/java/com/example/incidents/agent/IncidentAgent.java) is a small custom
orchestrator that runs these stages. Each stage is a separate Spring bean with a narrow job, so it can be
tested on its own. The knowledge base consists of the system description, the category definitions with
signal keywords and a fallback playbook, and the past incidents. It is plain files in
[src/main/resources/knowledge](src/main/resources/knowledge), so extending it does not require code
changes. The LLM sits behind the `LlmClient` interface. `AnthropicLlmClient` uses the official
Anthropic Java SDK, and the tests swap in `FakeLlmClient`.

### Handling model errors

The validator turns any model output into either a typed `IncidentAnalysis` or a list of
human-readable errors:

| Problem | Handling |
|---|---|
| JSON wrapped in markdown fences or surrounded by prose | Extracted without a retry (balanced-brace scan) |
| Invalid or truncated JSON | Rejected with the parser message, then regenerated |
| Object wrapped in `{"analysis": {...}}` | Unwrapped automatically |
| `nextSteps`, `steps` or `rationale` used instead of `next_steps` or `reasoning` | Renamed automatically |
| Extra fields such as `confidence` | Dropped automatically |
| `"High"`, `"critical"`, `"moderate"` | Normalized to `high` / `medium` / `low` |
| Category in a different letter case | Mapped to the canonical name |
| More than 3 hypotheses or more than 3 steps | Truncated automatically |
| Missing fields, too few steps, wrong types, severity outside the enum | JSON Schema validation, then regenerated |
| Category outside the allowed list | Rejected, regenerated with the list of allowed names |
| Text not in English (e.g. the model answered in the input language) | Script-based language check, then regenerated |
| API error, timeout, refusal, missing API key | No retry loop (the SDK already retries transient HTTP errors), immediate fallback |

Cheap, unambiguous fixes are applied locally and listed in `meta.repairs`. Everything else triggers a
correction turn: the agent appends the rejected answer and a message listing the exact validation
errors, so the model fixes its own output instead of starting over. If no attempt passes, the answer is
built by the rule-based fallback, so the endpoint always returns a valid contract.

## Testing

`./mvnw test` runs 27 tests without network access:

- [IncidentAgentTest](src/test/java/com/example/incidents/agent/IncidentAgentTest.java) scripts
  `FakeLlmClient` to return broken JSON, markdown-wrapped JSON, a Russian answer, an unknown category,
  a response with seven recoverable deviations, garbage on every attempt, and an API failure. It checks
  the number of attempts, the correction prompt sent back to the model, the repairs, and the fallback.
- [ResponseValidatorTest](src/test/java/com/example/incidents/agent/ResponseValidatorTest.java) covers
  the validator and the JSON Schema messages.
- [IncidentParserTest](src/test/java/com/example/incidents/agent/IncidentParserTest.java) covers fact
  extraction.
- [TriageScenariosTest](src/test/java/com/example/incidents/agent/TriageScenariosTest.java) runs the
  five incidents below through the pipeline with the LLM unavailable.
- [IncidentControllerTest](src/test/java/com/example/incidents/api/IncidentControllerTest.java) checks
  the HTTP contract, snake_case fields and the 400 on blank input.

Test incidents and expected outputs (also in [examples/requests.http](examples/requests.http)):

| # | Incident | Expected category / severity | What else should hold |
|---|---|---|---|
| 1 | Card payments fail, timeouts to PayGate since 12:05 UTC | External payment provider issue / high | Similar: INC-101. Steps mention the PayGate status page and per-provider error rate. |
| 2 | `/payments/create` p95 of 6 s, PostgreSQL CPU at 95%, long export queries from reporting-service, 504s | DB degradation caused by reporting / high | Similar: INC-102. Steps mention `pg_stat_activity` and reporting job schedules. |
| 3 | SMS one-time codes not delivered, 503 from the SMS gateway, payments fine | Notification delivery issue / medium | Similar: INC-103. Medium severity because money flows are not affected. |
| 4 | Users cannot log in, 401 and token signature errors, **written in Russian** | User authentication errors / high | `input_language: ru`, the whole answer is in English. Similar: INC-104. |
| 5 | Invoices were not generated overnight, NPE in a billing job | Other / unclassified / medium | No similar incident. Hypotheses should stay generic and must not invent a known pattern. |

The automated tests use the fallback to check the deterministic stages, so they do not depend on model
behaviour. With a real key, the same requests check the LLM path, where `meta.source` should be `llm`
and `meta.attempts` should usually be 1.

## Trade-offs

What I simplified to fit into about 3 hours:

- **Retrieval is keyword-based** (signal lists plus Jaccard overlap with a same-category bonus). With
  four past incidents, embeddings would add infrastructure without better results. For a real incident
  history I would use a vector store and hybrid search.
- **Language detection is a script heuristic** (Latin vs Cyrillic letters). It is enough to catch
  "answered in Russian" but not, say, French. A library such as Lingua would fix that.
- **Prompt-level JSON, not native structured outputs.** The Claude API can constrain output to a JSON
  Schema (`output_config.format`), which would remove most format errors. I kept the prompt-plus-validator
  approach because the assignment asks to show how invalid output is handled, and because it keeps the
  pipeline provider-agnostic. In production I would turn structured outputs on and keep the validator
  for semantic checks (category list, language, step quality).
- **The fallback is shallow**: one playbook hypothesis per category.
- **No persistence, auth or rate limiting**, and requests are synchronous.

What I would do with more time:

- An evaluation set of real incidents with expected categories, an LLM-as-judge for hypothesis quality,
  and tracking of accuracy and attempts per run.
- Tool use: let the agent query ELK, metrics and status pages itself instead of only suggesting steps.
- Prompt caching for the static system prompt, plus token and latency metrics per stage (Micrometer).
- A feedback loop: on-call engineers confirm or correct the category, and resolved incidents are added
  to the knowledge base.
