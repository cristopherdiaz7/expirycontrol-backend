package com.example.demo;

import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import pruebas.cierre.PedidoLentoController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// Desechabilidad: si la aplicación recibe la orden de apagarse mientras atiende un pedido,
// lo termina antes de cerrar (cierre controlado) y lo deja registrado en el log.
@ExtendWith(OutputCaptureExtension.class)
class GracefulShutdownTest {

    @Test
    void aRequestInProgressFinishesBeforeTheApplicationStops(CapturedOutput output) throws Exception {
        // Una base H2 propia: al cerrar esta aplicación se borran sus tablas, y no deben ser
        // las que usan los demás tests. Se pasa como argumento porque así tiene más prioridad
        // que application-test.yaml.
        ConfigurableApplicationContext app = new SpringApplicationBuilder(DemoApplication.class, PedidoLentoController.class)
                .profiles("test")
                .run("--server.port=0",
                        "--spring.datasource.url=jdbc:h2:mem:cierre-controlado;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        String port = app.getEnvironment().getProperty("local.server.port");
        String token = tokenFor(app);

        assertThat(app.getEnvironment().getProperty("spring.datasource.url")).contains("cierre-controlado");
        // La configuración del cierre controlado (application.yaml) está activa.
        assertThat(app.getEnvironment().getProperty("server.shutdown")).isEqualTo("graceful");
        assertThat(app.getEnvironment().getProperty("spring.lifecycle.timeout-per-shutdown-phase")).isEqualTo("30s");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<HttpResponse<String>> pedido = executor.submit(() -> HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/prueba/pedido-lento"))
                            .header("Authorization", "Bearer " + token).GET().build(),
                    HttpResponse.BodyHandlers.ofString()));

            // Se espera a que el pedido esté en curso y recién ahí se da la orden de apagado.
            assertThat(PedidoLentoController.EN_CURSO.await(10, TimeUnit.SECONDS)).isTrue();
            app.close();

            HttpResponse<String> respuesta = pedido.get(10, TimeUnit.SECONDS);
            assertThat(respuesta.statusCode()).isEqualTo(200);
            assertThat(respuesta.body()).isEqualTo("terminado");
        } finally {
            executor.shutdownNow();
        }

        assertThat(output.getAll())
                .contains("Commencing graceful shutdown")
                .contains("Graceful shutdown complete")
                .contains("Shutdown completed");
        assertThat(app.isActive()).isFalse();
    }

    private String tokenFor(ConfigurableApplicationContext app) {
        User user = app.getBean(UserRepository.class).save(User.builder()
                .name("Cierre")
                .email("cierre@example.com")
                .password(app.getBean(PasswordEncoder.class).encode("secreta123"))
                .build());
        return app.getBean(JwtService.class).generateToken(user.getEmail(), user.getId(), user.getName());
    }
}
