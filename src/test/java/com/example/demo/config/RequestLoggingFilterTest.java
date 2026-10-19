package com.example.demo.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Casos que no se pueden provocar desde la API sin inventar un endpoint: un error no controlado.
@ExtendWith(OutputCaptureExtension.class)
class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    @Test
    void anUnhandledErrorIsLoggedAs500WithItsRequestId(CapturedOutput output) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/products");
        request.addHeader(RequestLoggingFilter.REQUEST_ID_HEADER, "error-de-prueba");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] idWhileFailing = new String[1];
        FilterChain failingChain = (req, res) -> {
            idWhileFailing[0] = MDC.get(RequestLoggingFilter.MDC_KEY);
            throw new IllegalStateException("la base no responde");
        };

        assertThatThrownBy(() -> filter.doFilter(request, response, failingChain))
                .isInstanceOf(IllegalStateException.class);

        // Sin Spring el log usa el formato por defecto; que cada línea lleve [identificador]
        // lo comprueba RequestLoggingIntegrationTest con la configuración real.
        assertThat(response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER)).isEqualTo("error-de-prueba");
        assertThat(idWhileFailing[0]).isEqualTo("error-de-prueba");
        assertThat(output.getAll())
                .containsPattern("ERROR .*HTTP GET /products 500 \\d+ms")
                .contains("IllegalStateException: la base no responde");
    }

    @Test
    void theRequestIdDoesNotLeakIntoTheNextRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/products");
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            assertThat(MDC.get(RequestLoggingFilter.MDC_KEY)).isNotBlank();
        });

        assertThat(MDC.get(RequestLoggingFilter.MDC_KEY)).isNull();
    }

    @Test
    void onlyShortSafeIdsAreAccepted() {
        assertThat(RequestLoggingFilter.resolveRequestId("abc-123_X.9")).isEqualTo("abc-123_X.9");
        assertThat(RequestLoggingFilter.resolveRequestId(null)).hasSize(36);
        assertThat(RequestLoggingFilter.resolveRequestId("")).hasSize(36);
        assertThat(RequestLoggingFilter.resolveRequestId("a".repeat(65))).hasSize(36);
        assertThat(RequestLoggingFilter.resolveRequestId("x\nfalso")).hasSize(36);
    }
}
