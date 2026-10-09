package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

// Validación de conjunto: todo pasa por HTTP, igual que lo usa la aplicación,
// empezando por el registro y el login reales.
class FullFlowIntegrationTest extends IntegrationTestSupport {

    private static final Pattern TOKEN = Pattern.compile("\"token\":\"([^\"]+)\"");
    private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");

    private final LocalDate today = LocalDate.now();

    // --- Recorrido completo de un usuario ---

    @Test
    void fullJourneyOfOneUser() throws Exception {
        String token = registerAndLogin("Ana", "ana@example.com");

        // Alta de productos con precio.
        long expired = createProduct(token, "Leche", 3, today.minusDays(2), "1000.00");
        long expiresToday = createProduct(token, "Yogur", 2, today, "500.00");
        long soon = createProduct(token, "Queso", 1, today.plusDays(5), "4000.00");
        createProduct(token, "Arroz", 10, today.plusDays(60), "900.00");

        // Listado, vencimientos y estadísticas.
        mockMvc.perform(authorized(get("/products"), token)).andExpect(jsonPath("$.length()").value(4));
        mockMvc.perform(authorized(get("/products/expired"), token)).andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(authorized(get("/products/expiring?days=7"), token)).andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(authorized(get("/products/stats?days=7"), token))
                .andExpect(jsonPath("$.totalProducts").value(4))
                .andExpect(jsonPath("$.expiredProducts").value(2))
                .andExpect(jsonPath("$.expiringSoonProducts").value(1))
                .andExpect(jsonPath("$.validProducts").value(1));

        // Notificaciones: una por producto, en su categoría.
        mockMvc.perform(authorized(get("/notifications"), token))
                .andExpect(jsonPath("$.unreadCount").value(3))
                .andExpect(jsonPath("$.notifications[0].category").value("EXPIRED"))
                .andExpect(jsonPath("$.notifications[1].category").value("EXPIRES_TODAY"))
                .andExpect(jsonPath("$.notifications[2].category").value("WITHIN_7_DAYS"));

        // Lectura.
        mockMvc.perform(authorized(post("/notifications/" + expired + "/read"), token)).andExpect(jsonPath("$.unreadCount").value(2));
        mockMvc.perform(authorized(post("/notifications/read-all"), token)).andExpect(jsonPath("$.unreadCount").value(0));

        // Pérdidas: los dos vencidos, con su importe.
        mockMvc.perform(authorized(get("/losses/stats"), token))
                .andExpect(jsonPath("$.lossCount").value(2))
                .andExpect(jsonPath("$.unitsLost").value(5))
                .andExpect(jsonPath("$.totalAmount").value(4000.00));

        // Edición: el que vencía en 5 días pasa a vencer mañana y cambia de categoría.
        mockMvc.perform(authorized(put("/products/" + soon), token).contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Queso", 1, today.plusDays(1), "4000.00")))
                .andExpect(status().isOk());
        mockMvc.perform(authorized(get("/notifications"), token))
                .andExpect(jsonPath("$.unreadCount").value(1))
                .andExpect(jsonPath("$.notifications[2].category").value("WITHIN_3_DAYS"))
                .andExpect(jsonPath("$.notifications[2].read").value(false));

        // Edición de un vencido: su pérdida acompaña el cambio.
        mockMvc.perform(authorized(put("/products/" + expired), token).contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Leche", 5, today.minusDays(2), "1000.00")))
                .andExpect(status().isOk());
        mockMvc.perform(authorized(get("/losses/stats"), token)).andExpect(jsonPath("$.totalAmount").value(6000.00));

        // Eliminación: se van sus notificaciones y queda su pérdida.
        mockMvc.perform(authorized(delete("/products/" + expired), token)).andExpect(status().isNoContent());
        mockMvc.perform(authorized(get("/products"), token)).andExpect(jsonPath("$.length()").value(3));
        mockMvc.perform(authorized(get("/notifications"), token)).andExpect(jsonPath("$.notifications.length()").value(2));
        mockMvc.perform(authorized(get("/losses"), token))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].productName").value("Leche"))
                .andExpect(jsonPath("$[1].productDeleted").value(true));
        mockMvc.perform(authorized(get("/losses/stats"), token)).andExpect(jsonPath("$.totalAmount").value(6000.00));

        // Un segundo login sigue viendo lo mismo.
        String secondToken = login("ana@example.com");
        mockMvc.perform(authorized(get("/products"), secondToken)).andExpect(jsonPath("$.length()").value(3));
        mockMvc.perform(authorized(get("/products/" + expiresToday), secondToken)).andExpect(jsonPath("$.name").value("Yogur"));
    }

    // --- Aislamiento completo entre dos usuarios ---

    @Test
    void twoUsersNeverSeeOrChangeEachOthersData() throws Exception {
        String tokenA = registerAndLogin("Ana", "ana@example.com");
        String tokenB = registerAndLogin("Beto", "beto@example.com");

        long productOfA = createProduct(tokenA, "Leche de Ana", 3, today.minusDays(1), "1000.00");
        long productOfB = createProduct(tokenB, "Queso de Beto", 1, today.minusDays(1), "9000.00");

        // Productos y estadísticas.
        mockMvc.perform(authorized(get("/products"), tokenA))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Leche de Ana"));
        mockMvc.perform(authorized(get("/products/stats"), tokenA)).andExpect(jsonPath("$.totalProducts").value(1));
        mockMvc.perform(authorized(get("/products/expired"), tokenA)).andExpect(jsonPath("$.length()").value(1));

        // Acceso directo al producto ajeno: leer, modificar, eliminar y marcar su notificación.
        mockMvc.perform(authorized(get("/products/" + productOfB), tokenA)).andExpect(status().isNotFound());
        mockMvc.perform(authorized(put("/products/" + productOfB), tokenA).contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Robado", 1, today, "1.00")))
                .andExpect(status().isNotFound());
        mockMvc.perform(authorized(delete("/products/" + productOfB), tokenA)).andExpect(status().isNotFound());
        mockMvc.perform(authorized(post("/notifications/" + productOfB + "/read"), tokenA)).andExpect(status().isNotFound());

        // Notificaciones y estado de lectura.
        mockMvc.perform(authorized(post("/notifications/read-all"), tokenA)).andExpect(jsonPath("$.unreadCount").value(0));
        mockMvc.perform(authorized(get("/notifications"), tokenB))
                .andExpect(jsonPath("$.unreadCount").value(1))
                .andExpect(jsonPath("$.notifications[0].productName").value("Queso de Beto"))
                .andExpect(jsonPath("$.notifications[0].read").value(false));

        // Pérdidas.
        mockMvc.perform(authorized(get("/losses"), tokenA))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].productName").value("Leche de Ana"));
        mockMvc.perform(authorized(get("/losses/stats"), tokenA)).andExpect(jsonPath("$.totalAmount").value(3000.00));
        mockMvc.perform(authorized(get("/losses/stats"), tokenB)).andExpect(jsonPath("$.totalAmount").value(9000.00));

        // Lo que hace Ana con su producto no toca nada de Beto.
        mockMvc.perform(authorized(delete("/products/" + productOfA), tokenA)).andExpect(status().isNoContent());
        mockMvc.perform(authorized(get("/products/" + productOfB), tokenB)).andExpect(jsonPath("$.name").value("Queso de Beto"));
        mockMvc.perform(authorized(get("/losses"), tokenB)).andExpect(jsonPath("$.length()").value(1));
        assertEquals(2, lossRepository.count());
    }

    // --- Casos que cruzan funcionalidades ---

    @Test
    void productExpiringTodayIsExpiredNotifiedAsTodayAndLost() throws Exception {
        String token = registerAndLogin("Ana", "ana@example.com");
        createProduct(token, "Yogur", 4, today, "250.00");

        mockMvc.perform(authorized(get("/products/expired"), token)).andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(authorized(get("/products/stats"), token)).andExpect(jsonPath("$.expiredProducts").value(1));
        mockMvc.perform(authorized(get("/notifications"), token))
                .andExpect(jsonPath("$.notifications[0].category").value("EXPIRES_TODAY"))
                .andExpect(jsonPath("$.notifications[0].severity").value("EXPIRED"));
        mockMvc.perform(authorized(get("/losses"), token))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].totalAmount").value(1000.00));
    }

    @Test
    void zeroQuantityOrZeroPriceGivesLossOfZero() throws Exception {
        String token = registerAndLogin("Ana", "ana@example.com");
        createProduct(token, "Sin stock", 0, today.minusDays(1), "1500.00");
        createProduct(token, "Muestra gratis", 6, today.minusDays(1), "0");

        mockMvc.perform(authorized(get("/losses/stats"), token))
                .andExpect(jsonPath("$.lossCount").value(2))
                .andExpect(jsonPath("$.unitsLost").value(6))
                .andExpect(jsonPath("$.totalAmount").value(0));
    }

    @Test
    void largestAllowedProductDoesNotBreakLosses() throws Exception {
        String token = registerAndLogin("Ana", "ana@example.com");
        createProduct(token, "Lote grande", 1000000, today.minusDays(1), "9999999999.99");

        mockMvc.perform(authorized(get("/losses"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(authorized(get("/losses/stats"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lossCount").value(1));
    }

    @Test
    void productListKeepsItsOrderAfterEditing() throws Exception {
        String token = registerAndLogin("Ana", "ana@example.com");
        long first = createProduct(token, "Primero", 1, today.plusDays(30), "10.00");
        createProduct(token, "Segundo", 1, today.plusDays(20), "10.00");
        createProduct(token, "Tercero", 1, today.plusDays(10), "10.00");

        mockMvc.perform(authorized(put("/products/" + first), token).contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Primero editado", 9, today.plusDays(30), "10.00")))
                .andExpect(status().isOk());

        mockMvc.perform(authorized(get("/products"), token))
                .andExpect(jsonPath("$[0].name").value("Primero editado"))
                .andExpect(jsonPath("$[1].name").value("Segundo"))
                .andExpect(jsonPath("$[2].name").value("Tercero"));
        mockMvc.perform(authorized(get("/products/expiring?days=60"), token))
                .andExpect(jsonPath("$[0].name").value("Primero editado"))
                .andExpect(jsonPath("$[2].name").value("Tercero"));
    }

    // --- Entradas fuera de los límites: siempre 400 con JSON, nunca un error interno ---

    @Test
    void oversizedInputsAreRejectedWith400() throws Exception {
        String token = registerAndLogin("Ana", "ana@example.com");
        String longText = "x".repeat(300);

        mockMvc.perform(authorized(post("/products"), token).contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(longText, 1, today.plusDays(5), "10.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.name").exists());

        mockMvc.perform(authorized(post("/products"), token).contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Leche", 1000001, today.plusDays(5), "10.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.quantity").exists());

        mockMvc.perform(authorized(post("/products"), token).contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Leche", 1, today.plusDays(5), "12345678901.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.unitPrice").exists());

        assertEquals(0, productRepository.count());
    }

    @Test
    void oversizedRegistrationFieldsAreRejectedWith400() throws Exception {
        mockMvc.perform(post("/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + "n".repeat(300) + "\",\"email\":\"largo@example.com\",\"password\":\"secreta123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.name").exists());

        mockMvc.perform(post("/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ana\",\"email\":\"clave-larga@example.com\",\"password\":\"" + "p".repeat(100) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.password").exists());

        // 40 eñes son 40 caracteres pero 80 bytes: superan el límite de BCrypt.
        mockMvc.perform(post("/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ana\",\"email\":\"clave-bytes@example.com\",\"password\":\"" + "ñ".repeat(40) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.password").value("La contraseña es demasiado larga"));

        assertEquals(0, userRepository.count());
    }

    @Test
    void loginWithOversizedPasswordIsRejectedWithoutError() throws Exception {
        registerAndLogin("Ana", "ana@example.com");

        mockMvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@example.com\",\"password\":\"" + "p".repeat(200) + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Email o contraseña incorrectos"));
    }

    private String registerAndLogin(String name, String email) throws Exception {
        mockMvc.perform(post("/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"email\":\"" + email + "\",\"password\":\"secreta123\"}"))
                .andExpect(status().isCreated());
        return login(email);
    }

    private String login(String email) throws Exception {
        String body = mockMvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"secreta123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Matcher matcher = TOKEN.matcher(body);
        if (!matcher.find()) {
            throw new IllegalStateException("El login no devolvió token");
        }
        return matcher.group(1);
    }

    private long createProduct(String token, String name, int quantity, LocalDate expirationDate, String unitPrice) throws Exception {
        String body = mockMvc.perform(authorized(post("/products"), token).contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(name, quantity, expirationDate, unitPrice)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Matcher matcher = ID.matcher(body);
        if (!matcher.find()) {
            throw new IllegalStateException("La respuesta no tiene id");
        }
        return Long.parseLong(matcher.group(1));
    }

    private String productJson(String name, int quantity, LocalDate expirationDate, String unitPrice) {
        return "{\"name\":\"" + name + "\",\"description\":\"Descripcion\",\"category\":\"Frescos\",\"quantity\":" + quantity
                + ",\"expirationDate\":\"" + expirationDate + "\",\"unitPrice\":" + unitPrice + "}";
    }
}
