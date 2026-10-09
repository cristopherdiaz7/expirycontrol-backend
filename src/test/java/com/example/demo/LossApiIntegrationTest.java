package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.model.Loss;
import com.example.demo.model.Product;
import com.example.demo.model.User;
import com.example.demo.repository.LossRepository;
import com.example.demo.repository.NotificationReadRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.JwtService;
import com.example.demo.service.LossService;
import jakarta.servlet.Filter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class LossApiIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private LossRepository lossRepository;

    @Autowired
    private NotificationReadRepository notificationReadRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private LossService lossService;

    private MockMvc mockMvc;
    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();

        cleanDatabase();

        userA = saveUser("Usuario A", "a@example.com");
        userB = saveUser("Usuario B", "b@example.com");
    }

    @AfterEach
    void cleanDatabase() {
        lossRepository.deleteAll();
        notificationReadRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
    }

    // --- Precio unitario ---

    @Test
    void priceIsRequiredAndValidated() throws Exception {
        expectPriceError("", "El precio unitario es obligatorio");
        expectPriceError(",\"unitPrice\":-1", "El precio unitario no puede ser negativo");
        expectPriceError(",\"unitPrice\":10.999", "El precio unitario admite hasta 10 dígitos enteros y 2 decimales");

        assertEquals(0, productRepository.count());
    }

    @Test
    void zeroPriceIsAcceptedAndPriceIsReturned() throws Exception {
        mockMvc.perform(authorized(post("/products"), userA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Muestra gratis", 5, LocalDate.now().plusDays(10), "0")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.unitPrice").value(0));

        mockMvc.perform(authorized(post("/products"), userA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Leche", 5, LocalDate.now().plusDays(10), "1250.50")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.unitPrice").value(1250.50));
    }

    @Test
    void productCreatedBeforePricesHasNoPriceUntilEdited() throws Exception {
        Product legacy = saveProduct("Producto viejo", userA, 10, 2, null);

        mockMvc.perform(authorized(get("/products/" + legacy.getId()), userA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitPrice").isEmpty());

        mockMvc.perform(authorized(put("/products/" + legacy.getId()), userA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Producto viejo", 2, LocalDate.now().plusDays(10), "300.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitPrice").value(300.00));
    }

    // --- Registro de pérdidas ---

    @Test
    void expiredProductWithPriceGeneratesLossWithTotalAmount() throws Exception {
        Product expired = saveProduct("Leche", userA, -2, 3, "1250.50");
        saveProduct("Yogur", userA, 0, 4, "800.00");
        saveProduct("Arroz", userA, 30, 10, "900.00");

        mockMvc.perform(authorized(get("/losses"), userA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                // De la más reciente a la más antigua: primero el que vence hoy.
                .andExpect(jsonPath("$[0].productName").value("Yogur"))
                .andExpect(jsonPath("$[0].totalAmount").value(3200.00))
                .andExpect(jsonPath("$[1].productId").value(expired.getId()))
                .andExpect(jsonPath("$[1].productName").value("Leche"))
                .andExpect(jsonPath("$[1].quantity").value(3))
                .andExpect(jsonPath("$[1].unitPrice").value(1250.50))
                .andExpect(jsonPath("$[1].expirationDate").value(LocalDate.now().minusDays(2).toString()))
                .andExpect(jsonPath("$[1].totalAmount").value(3751.50))
                .andExpect(jsonPath("$[1].productDeleted").value(false));
    }

    @Test
    void expiredProductWithoutPriceGeneratesNoLoss() throws Exception {
        saveProduct("Producto viejo", userA, -5, 3, null);

        mockMvc.perform(authorized(get("/losses"), userA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        assertEquals(0, lossRepository.count());
    }

    @Test
    void sameLossIsNeverCountedTwice() throws Exception {
        saveProduct("Leche", userA, -2, 3, "1000.00");

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(authorized(get("/losses"), userA)).andExpect(status().isOk());
            mockMvc.perform(authorized(get("/losses/stats"), userA)).andExpect(status().isOk());
        }

        assertEquals(1, lossRepository.count());
        mockMvc.perform(authorized(get("/losses/stats"), userA))
                .andExpect(jsonPath("$.lossCount").value(1))
                .andExpect(jsonPath("$.totalAmount").value(3000.00));
    }

    @Test
    void simultaneousRequestsRecordTheLossOnlyOnce() throws Exception {
        saveProduct("Leche", userA, -2, 3, "1000.00");
        saveProduct("Yogur", userA, -1, 1, "500.00");
        Long userId = userA.getId();

        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Object>> tasks = new ArrayList<>();
            for (int i = 0; i < 16; i++) {
                tasks.add(i % 2 == 0 ? () -> lossService.getLosses(userId, null) : () -> lossService.getStats(userId, null));
            }
            for (Future<Object> result : executor.invokeAll(tasks)) {
                result.get();
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(2, lossRepository.count());
    }

    @Test
    void lossFollowsProductCorrections() throws Exception {
        Product product = saveProduct("Leche", userA, -2, 3, "1000.00");
        mockMvc.perform(authorized(get("/losses"), userA))
                .andExpect(jsonPath("$[0].totalAmount").value(3000.00));

        mockMvc.perform(authorized(put("/products/" + product.getId()), userA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Leche descremada", 5, LocalDate.now().minusDays(2), "1200.00")))
                .andExpect(status().isOk());

        mockMvc.perform(authorized(get("/losses"), userA))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].productName").value("Leche descremada"))
                .andExpect(jsonPath("$[0].quantity").value(5))
                .andExpect(jsonPath("$[0].unitPrice").value(1200.00))
                .andExpect(jsonPath("$[0].totalAmount").value(6000.00));
        assertEquals(1, lossRepository.count());
    }

    @Test
    void lossIsRemovedWhenDateBecomesFuture() throws Exception {
        Product product = saveProduct("Leche", userA, -2, 3, "1000.00");
        mockMvc.perform(authorized(get("/losses"), userA))
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(authorized(put("/products/" + product.getId()), userA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Leche", 3, LocalDate.now().plusDays(20), "1000.00")))
                .andExpect(status().isOk());

        mockMvc.perform(authorized(get("/losses"), userA))
                .andExpect(jsonPath("$.length()").value(0));
        assertEquals(0, lossRepository.count());
    }

    // --- Historial al eliminar el producto ---

    @Test
    void lossIsKeptWhenProductIsDeleted() throws Exception {
        Product product = saveProduct("Leche", userA, -2, 3, "1000.00");
        mockMvc.perform(authorized(get("/losses"), userA))
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(authorized(delete("/products/" + product.getId()), userA))
                .andExpect(status().isNoContent());

        assertEquals(0, productRepository.count());
        mockMvc.perform(authorized(get("/losses"), userA))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].productName").value("Leche"))
                .andExpect(jsonPath("$[0].totalAmount").value(3000.00))
                .andExpect(jsonPath("$[0].productId").isEmpty())
                .andExpect(jsonPath("$[0].productDeleted").value(true));

        // Las consultas siguientes no la modifican ni la duplican.
        mockMvc.perform(authorized(get("/losses/stats"), userA))
                .andExpect(jsonPath("$.lossCount").value(1))
                .andExpect(jsonPath("$.totalAmount").value(3000.00));
        assertEquals(1, lossRepository.count());
    }

    @Test
    void deletingExpiredProductRecordsLossEvenIfLossesWereNeverQueried() throws Exception {
        Product product = saveProduct("Yogur", userA, -1, 2, "500.00");

        mockMvc.perform(authorized(delete("/products/" + product.getId()), userA))
                .andExpect(status().isNoContent());

        Loss loss = lossRepository.findAll().get(0);
        assertEquals("Yogur", loss.getProductName());
        assertEquals(0, new BigDecimal("1000.00").compareTo(loss.getTotalAmount()));
        assertNull(loss.getProduct());
    }

    @Test
    void deletingProductThatIsNotExpiredRecordsNoLoss() throws Exception {
        Product product = saveProduct("Arroz", userA, 30, 2, "500.00");

        mockMvc.perform(authorized(delete("/products/" + product.getId()), userA))
                .andExpect(status().isNoContent());

        assertEquals(0, lossRepository.count());
    }

    @Test
    void newProductWithSameNameAsDeletedOneGetsItsOwnLoss() throws Exception {
        Product first = saveProduct("Leche", userA, -3, 1, "1000.00");
        mockMvc.perform(authorized(delete("/products/" + first.getId()), userA)).andExpect(status().isNoContent());
        saveProduct("Leche", userA, -1, 2, "1000.00");

        mockMvc.perform(authorized(get("/losses/stats"), userA))
                .andExpect(jsonPath("$.lossCount").value(2))
                .andExpect(jsonPath("$.unitsLost").value(3))
                .andExpect(jsonPath("$.totalAmount").value(3000.00));
    }

    // --- Estadísticas ---

    @Test
    void statsReturnTotalsAndAmountsByMonth() throws Exception {
        saveProduct("Leche", userA, -1, 3, "1000.00");
        saveProduct("Yogur", userA, -1, 2, "250.25");
        saveProduct("Queso", userA, -45, 1, "4000.00");
        saveProduct("Arroz", userA, 30, 10, "900.00");

        String recentMonth = YearMonth.from(LocalDate.now().minusDays(1)).toString();
        String olderMonth = YearMonth.from(LocalDate.now().minusDays(45)).toString();

        mockMvc.perform(authorized(get("/losses/stats"), userA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("ARS"))
                .andExpect(jsonPath("$.totalAmount").value(7500.50))
                .andExpect(jsonPath("$.lossCount").value(3))
                .andExpect(jsonPath("$.unitsLost").value(6))
                .andExpect(jsonPath("$.byMonth.length()").value(2))
                .andExpect(jsonPath("$.byMonth[0].month").value(recentMonth))
                .andExpect(jsonPath("$.byMonth[0].totalAmount").value(3500.50))
                .andExpect(jsonPath("$.byMonth[0].lossCount").value(2))
                .andExpect(jsonPath("$.byMonth[0].unitsLost").value(5))
                .andExpect(jsonPath("$.byMonth[1].month").value(olderMonth))
                .andExpect(jsonPath("$.byMonth[1].totalAmount").value(4000.00));
    }

    @Test
    void statsAreZeroWithoutLosses() throws Exception {
        mockMvc.perform(authorized(get("/losses/stats"), userA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value(0))
                .andExpect(jsonPath("$.lossCount").value(0))
                .andExpect(jsonPath("$.unitsLost").value(0))
                .andExpect(jsonPath("$.byMonth.length()").value(0));
    }

    // --- Aislamiento y fecha del cliente ---

    @Test
    void lossesAreIsolatedBetweenUsers() throws Exception {
        saveProduct("Leche de A", userA, -2, 3, "1000.00");
        saveProduct("Queso de B", userB, -2, 1, "5000.00");

        mockMvc.perform(authorized(get("/losses"), userA))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].productName").value("Leche de A"));
        mockMvc.perform(authorized(get("/losses/stats"), userA))
                .andExpect(jsonPath("$.totalAmount").value(3000.00));

        mockMvc.perform(authorized(get("/losses"), userB))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].productName").value("Queso de B"));
        mockMvc.perform(authorized(get("/losses/stats"), userB))
                .andExpect(jsonPath("$.totalAmount").value(5000.00));
    }

    @Test
    void lossesRequireAuthentication() throws Exception {
        mockMvc.perform(get("/losses")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/losses/stats")).andExpect(status().isUnauthorized());
    }

    @Test
    void clientDateDecidesWhetherProductIsLost() throws Exception {
        // Vence hoy para el servidor; para un cliente que todavía está en ayer, aún no venció.
        Product product = saveProduct("Leche", userA, 0, 3, "1000.00");
        String yesterday = LocalDate.now().minusDays(1).toString();

        mockMvc.perform(authorized(get("/losses?today=" + yesterday), userA))
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(authorized(delete("/products/" + product.getId() + "?today=" + yesterday), userA))
                .andExpect(status().isNoContent());
        assertEquals(0, lossRepository.count());

        mockMvc.perform(authorized(get("/losses?today=" + LocalDate.now().plusDays(5)), userA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("La fecha enviada no coincide con la fecha actual"));
    }

    private void expectPriceError(String priceFragment, String message) throws Exception {
        String body = "{\"name\":\"Leche\",\"description\":\"Entera\",\"category\":\"Bebidas\",\"quantity\":1,\"expirationDate\":\"2030-01-15\"" + priceFragment + "}";

        mockMvc.perform(authorized(post("/products"), userA).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.unitPrice").value(message));
    }

    private MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request, User user) {
        return request.header("Authorization", "Bearer " + jwtService.generateToken(user.getEmail(), user.getId(), user.getName()));
    }

    private String productJson(String name, int quantity, LocalDate expirationDate, String unitPrice) {
        return "{\"name\":\"" + name + "\",\"description\":\"Descripcion\",\"category\":\"Frescos\",\"quantity\":" + quantity
                + ",\"expirationDate\":\"" + expirationDate + "\",\"unitPrice\":" + unitPrice + "}";
    }

    private User saveUser(String name, String email) {
        return userRepository.save(User.builder()
                .name(name)
                .email(email)
                .password(passwordEncoder.encode("secreta123"))
                .build());
    }

    private Product saveProduct(String name, User owner, int daysFromToday, int quantity, String unitPrice) {
        return productRepository.save(Product.builder()
                .name(name)
                .description("Descripcion")
                .category("Frescos")
                .quantity(quantity)
                .expirationDate(LocalDate.now().plusDays(daysFromToday))
                .unitPrice(unitPrice == null ? null : new BigDecimal(unitPrice))
                .user(owner)
                .build());
    }
}
