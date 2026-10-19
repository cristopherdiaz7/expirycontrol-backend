package com.example.demo;

import com.example.demo.config.RequestLoggingFilter;
import com.example.demo.model.User;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Cada petición deja una sola línea identificable en el log, con el mismo identificador
// que se devuelve en la cabecera X-Request-Id, y sin datos sensibles.
@ExtendWith(OutputCaptureExtension.class)
class RequestLoggingIntegrationTest extends IntegrationTestSupport {

    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @Autowired
    private RequestLoggingFilter requestLoggingFilter;

    // Igual que en la aplicación: el filtro de registro va antes que la cadena de seguridad.
    @BeforeEach
    void addRequestLoggingFilter() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(requestLoggingFilter, context.getBean("springSecurityFilterChain", Filter.class))
                .build();
    }

    @Test
    void eachRequestGetsAnIdThatIsReturnedAndLoggedOnce(CapturedOutput output) throws Exception {
        User user = saveUser("Ana", "ana@example.com");

        MvcResult result = mockMvc.perform(authorized(get("/products"), user))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestLoggingFilter.REQUEST_ID_HEADER, org.hamcrest.Matchers.matchesPattern(UUID_PATTERN)))
                .andReturn();

        String requestId = result.getResponse().getHeader(RequestLoggingFilter.REQUEST_ID_HEADER);
        List<String> lines = linesWith(output, requestId);
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0))
                .contains("[" + requestId + "]")
                .containsPattern("HTTP GET /products 200 \\d+ms");
    }

    @Test
    void twoRequestsGetDifferentIds() throws Exception {
        String first = mockMvc.perform(get("/actuator/info")).andReturn()
                .getResponse().getHeader(RequestLoggingFilter.REQUEST_ID_HEADER);
        String second = mockMvc.perform(get("/actuator/info")).andReturn()
                .getResponse().getHeader(RequestLoggingFilter.REQUEST_ID_HEADER);

        assertThat(first).isNotBlank();
        assertThat(second).isNotBlank().isNotEqualTo(first);
    }

    @Test
    void aValidIdSentByTheClientIsKept(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/actuator/info").header(RequestLoggingFilter.REQUEST_ID_HEADER, "demo-examen-01"))
                .andExpect(header().string(RequestLoggingFilter.REQUEST_ID_HEADER, "demo-examen-01"));

        assertThat(linesWith(output, "demo-examen-01")).hasSize(1);
    }

    @Test
    void aMalformedIdIsReplaced(CapturedOutput output) throws Exception {
        String malicious = "x\r\nHTTP GET /falso 200 1ms";
        String tooLong = "a".repeat(65);

        for (String received : List.of(malicious, tooLong, "con espacios")) {
            mockMvc.perform(get("/actuator/info").header(RequestLoggingFilter.REQUEST_ID_HEADER, received))
                    .andExpect(header().string(RequestLoggingFilter.REQUEST_ID_HEADER, org.hamcrest.Matchers.matchesPattern(UUID_PATTERN)));
        }

        assertThat(output.getAll()).doesNotContain("/falso").doesNotContain(tooLong);
    }

    @Test
    void unauthorizedRequestsAreLoggedWithTheirStatus(CapturedOutput output) throws Exception {
        MvcResult result = mockMvc.perform(get("/products"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        String requestId = result.getResponse().getHeader(RequestLoggingFilter.REQUEST_ID_HEADER);
        assertThat(linesWith(output, requestId)).singleElement()
                .asString().containsPattern("HTTP GET /products 401 \\d+ms");
    }

    @Test
    void credentialsTokensAndEmailsNeverReachTheLog(CapturedOutput output) throws Exception {
        saveUser("Privado", "privado@example.com");

        MvcResult login = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"privado@example.com\",\"password\":\"secreta123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String token = com.jayway.jsonpath.JsonPath.read(login.getResponse().getContentAsString(), "$.token");

        mockMvc.perform(get("/products?today=2026-10-19").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(output.getAll())
                .contains("HTTP POST /login 200")
                .contains("HTTP GET /products 200")
                .doesNotContain("secreta123")
                .doesNotContain("privado@example.com")
                .doesNotContain(token)
                .doesNotContain("Bearer")
                .doesNotContain("today=");
    }

    @Test
    void healthProbesDoNotFillTheLog(CapturedOutput output) throws Exception {
        String requestId = mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(RequestLoggingFilter.REQUEST_ID_HEADER);

        assertThat(requestId).isNotBlank();
        assertThat(linesWith(output, requestId)).isEmpty();
    }

    private List<String> linesWith(CapturedOutput output, String text) {
        return Arrays.stream(output.getAll().split("\\R"))
                .filter(line -> line.contains(text))
                .toList();
    }
}
