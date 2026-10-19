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

import static org.assertj.core.api.Assertions.assertThat;

// Con un servidor real: el filtro se registra solo, corre antes que Spring Security
// (por eso registra el 401) y anota cada petición una sola vez.
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
}
