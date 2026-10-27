package com.example.incidents.agent;

import com.example.incidents.llm.FakeLlmClient;
import com.example.incidents.model.AnalysisMeta;
import com.example.incidents.model.AnalysisResult;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TriageScenariosTest {

    static Stream<Arguments> scenarios() {
        return Stream.of(
                Arguments.of(
                        TestFixtures.PAYGATE_INCIDENT,
                        "External payment provider issue", "high", "INC-101"),
                Arguments.of("""
                                p95 latency of /payments/create jumped to 6 seconds after 02:00 UTC.
                                PostgreSQL CPU is at 95% and pg_stat_activity shows long-running export queries from reporting-service.
                                api-gateway returns 504 Gateway Timeout to some clients.
                                """,
                        "DB degradation caused by reporting", "high", "INC-102"),
                Arguments.of("""
                                Users are not receiving SMS one-time codes for top-up confirmation since 09:40 UTC.
                                notification-service logs show HTTP 503 responses from the SMS gateway.
                                Payments and balances are processed normally.
                                """,
                        "Notification delivery issue", "medium", "INC-103"),
                Arguments.of("""
                                Пользователи не могут войти в мобильное приложение. auth-service отвечает 401,
                                в логах ошибки проверки подписи токена. Остальные сервисы работают штатно.
                                """,
                        "User authentication errors", "high", "INC-104"),
                Arguments.of("""
                                Monthly invoices for corporate clients were not generated overnight.
                                billing-service logs show a NullPointerException in InvoiceGenerationJob.
                                No customer complaints so far.
                                """,
                        "Other / unclassified", "medium", null)
        );
    }

    @ParameterizedTest
    @MethodSource("scenarios")
    void fallbackClassifiesKnownIncidentPatterns(String description, String category, String severity, String similarIncident) {
        FakeLlmClient unavailable = new FakeLlmClient().fail("ANTHROPIC_API_KEY is not configured");

        AnalysisResult result = TestFixtures.agent(unavailable).analyze(description);

        assertThat(result.meta().source()).isEqualTo(AnalysisMeta.Source.FALLBACK);
        assertThat(result.analysis().category()).isEqualTo(category);
        assertThat(result.analysis().severity()).isEqualTo(severity);
        assertThat(result.analysis().hypotheses()).singleElement()
                .satisfies(hypothesis -> assertThat(hypothesis.nextSteps()).hasSizeBetween(2, 3));
        if (similarIncident == null) {
            assertThat(result.meta().similarIncidents()).isEmpty();
        } else {
            assertThat(result.meta().similarIncidents()).first().isEqualTo(similarIncident);
        }
    }
}
