package pruebas.cierre;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CountDownLatch;

// Solo para GracefulShutdownTest: un pedido que tarda 3 segundos.
// Está fuera del paquete com.example.demo para que la aplicación no lo cargue en los demás tests.
@RestController
public class PedidoLentoController {

    public static final CountDownLatch EN_CURSO = new CountDownLatch(1);

    @GetMapping("/prueba/pedido-lento")
    public String pedidoLento() throws InterruptedException {
        EN_CURSO.countDown();
        Thread.sleep(3000);
        return "terminado";
    }
}
