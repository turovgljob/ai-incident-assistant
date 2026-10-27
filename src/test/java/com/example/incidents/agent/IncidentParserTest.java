package com.example.incidents.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class IncidentParserTest {

    private final IncidentParser parser = TestFixtures.parser();

    @Test
    void extractsFactsFromEnglishDescription() {
        ParsedIncident incident = parser.parse("""
                Sharp increase in response time for /payments/create (up to 5-7 seconds) since 14:30 UTC.
                DB dashboards show high CPU and many long-running queries from reporting-service.
                Some customers receive 504 Gateway Timeout from api-gateway.
                """);

        assertThat(incident.language()).isEqualTo("en");
        assertThat(incident.services()).containsExactly("api-gateway", "payment-service", "reporting-service");
        assertThat(incident.externalDependencies()).containsExactly("PostgreSQL");
        assertThat(incident.statusCodes()).containsExactly("504");
        assertThat(incident.times()).containsExactly("14:30 UTC");
        assertThat(incident.endpoints()).containsExactly("/payments/create");
        assertThat(incident.keywords()).contains("reporting", "queries").doesNotContain("from", "the");
    }

    @Test
    void recognisesRussianDescription() {
        ParsedIncident incident = parser.parse(
                "Пользователи не могут войти в приложение, auth-service отвечает 401, в логах ошибки подписи токена.");

        assertThat(incident.language()).isEqualTo("ru");
        assertThat(incident.services()).containsExactly("auth-service");
        assertThat(incident.statusCodes()).containsExactly("401");
    }

    @Test
    void recognisesGenericErrorClasses() {
        ParsedIncident incident = parser.parse("PayGate returns 5xx errors and SMS delivery fails.");

        assertThat(incident.statusCodes()).containsExactly("5xx");
        assertThat(incident.externalDependencies()).containsExactly("PayGate", "SMS provider");
    }

    @Test
    void rejectsBlankInput() {
        assertThatIllegalArgumentException().isThrownBy(() -> parser.parse("   "));
    }
}
