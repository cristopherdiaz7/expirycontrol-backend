package com.example.demo;

import com.example.demo.config.RequestLoggingFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Con un servidor real: el filtro se registra solo, corre antes que Spring Security
// (por eso registra el 401), anota cada petición una sola vez y recibe el id del usuario
// que deja el filtro JWT.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class RequestLoggingServerTest {

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void theRealServerLogsEveryRequestOnceWithItsId(CapturedOutput output) throws Exception {
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/products")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(401);
        String requestId = response.headers().firstValue(RequestLoggingFilter.REQUEST_ID_HEADER).orElseThrow();

        long lines = Arrays.stream(output.getAll().split("\\R"))
                .filter(line -> line.contains("[" + requestId + "]") && line.contains("HTTP GET /products 401"))
                .count();
        assertThat(lines).isEqualTo(1);
    }

    @Test
    void theRealServerLogsTheUserOfAnAuthenticatedRequest(CapturedOutput output) throws Exception {
        String email = "servidor-" + UUID.randomUUID() + "@example.com";
        HttpResponse<String> registered = post("/register",
                "{\"name\":\"Servidor\",\"email\":\"" + email + "\",\"password\":\"secreta123\"}");
        assertThat(registered.statusCode()).isEqualTo(201);
        String userId = registered.body().replaceAll(".*\"id\":(\\d+).*", "$1");

        HttpResponse<String> login = post("/login", "{\"email\":\"" + email + "\",\"password\":\"secreta123\"}");
        String token = login.body().replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");

        HttpResponse<String> products = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/products"))
                        .header("Authorization", "Bearer " + token).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(products.statusCode()).isEqualTo(200);
        String requestId = products.headers().firstValue(RequestLoggingFilter.REQUEST_ID_HEADER).orElseThrow();

        List<String> lines = Arrays.stream(output.getAll().split("\\R"))
                .filter(line -> line.contains("[" + requestId + "]"))
                .toList();
        assertThat(lines).singleElement().asString().endsWith(" usuario=" + userId);
        assertThat(output.getAll()).doesNotContain(email).doesNotContain(token);
    }

    private HttpResponse<String> post(String path, String json) throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json)).build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
