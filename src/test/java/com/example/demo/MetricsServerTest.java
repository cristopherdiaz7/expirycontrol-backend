package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Métricas de la aplicación: públicas, en formato de Prometheus y sin datos personales.
// Con un servidor real, porque las métricas de peticiones las mide un filtro de Tomcat.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MetricsServerTest {

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void prometheusIsPublicAndCountsEachRequestByRouteAndStatus() throws Exception {
        get("/actuator/info");
        get("/products");

        HttpResponse<String> metrics = get("/actuator/prometheus");

        assertThat(metrics.statusCode()).isEqualTo(200);
        assertThat(metrics.headers().firstValue("Content-Type")).hasValueSatisfying(type -> assertThat(type).startsWith("text/plain"));
        assertThat(metrics.body())
                .contains("http_server_requests_seconds_count")
                .containsPattern("http_server_requests_seconds_count\\{[^}]*status=\"200\"[^}]*uri=\"/actuator/info\"")
                .containsPattern("http_server_requests_seconds_count\\{[^}]*status=\"401\"")
                .contains("hikaricp_connections")
                .contains("jvm_memory_used_bytes");
    }

    @Test
    void metricsListIsPublic() throws Exception {
        HttpResponse<String> metrics = get("/actuator/metrics");

        assertThat(metrics.statusCode()).isEqualTo(200);
        assertThat(metrics.body()).contains("\"names\"").contains("hikaricp.connections");
    }

    @Test
    void metricsNeverContainPersonalData() throws Exception {
        String email = "metricas-" + UUID.randomUUID() + "@example.com";
        HttpResponse<String> registered = post("/register",
                "{\"name\":\"NombreMetricas\",\"email\":\"" + email + "\",\"password\":\"secreta123\"}");
        assertThat(registered.statusCode()).isEqualTo(201);
        String userId = registered.body().replaceAll(".*\"id\":(\\d+).*", "$1");
        get("/products/" + userId);

        String body = get("/actuator/prometheus").body();

        assertThat(body)
                .doesNotContain(email)
                .doesNotContain("NombreMetricas")
                .doesNotContain("secreta123")
                // Las rutas se agrupan por plantilla: un id concreto no aparece en las métricas.
                .doesNotContain("uri=\"/products/" + userId + "\"");
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String json) throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json)).build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
