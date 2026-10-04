package com.example.demo;
// Importamos Map para devolver respuestas JSON.
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController //indica la clase que manejara
public class HelloController {

    @GetMapping("/hello") //define la ruta
    public Map<String, String> hello() {
        return Map.of("greeting", "Hello World");
    }

    @GetMapping("/login")
    public Map<String, String> login() {
        return Map.of("message", "Login exitoso");
    }

    @GetMapping("/estado")
    public Map<String, String> estado() {
        return Map.of("estado", "Producto por vencer");
    }

    @GetMapping("/vencidos")
    public Map<String, String> vencidos() {
        return Map.of("productos", "Hay 3 productos vencidos");
    }

    @GetMapping("/estadisticas")
    public Map<String, Integer> estadisticas() {
        return Map.of("productosVencidos", 5);
    }
}
