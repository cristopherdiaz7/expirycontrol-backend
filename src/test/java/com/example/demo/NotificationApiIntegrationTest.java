package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.model.Product;
import com.example.demo.model.User;
import com.example.demo.repository.NotificationReadRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.JwtService;
import jakarta.servlet.Filter;
import java.time.LocalDate;
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
class NotificationApiIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private NotificationReadRepository notificationReadRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private MockMvc mockMvc;
    private User userA;
    private User userB;
    private Product expiredOfA;
    private Product soonOfA;
    private Product farOfA;
    private Product soonOfB;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();

        cleanDatabase();

        userA = saveUser("Usuario A", "a@example.com");
        userB = saveUser("Usuario B", "b@example.com");
        expiredOfA = saveProduct("Leche de A", userA, -1);
        soonOfA = saveProduct("Yogur de A", userA, 6);
        farOfA = saveProduct("Arroz de A", userA, 60);
        soonOfB = saveProduct("Queso de B", userB, 2);
    }

    @AfterEach
    void cleanDatabase() {
        notificationReadRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void listReturnsOwnNotificationsWithUnreadCount() throws Exception {
        mockMvc.perform(authorized(get("/notifications"), userA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(2))
                .andExpect(jsonPath("$.notifications.length()").value(2))
                .andExpect(jsonPath("$.notifications[0].productId").value(expiredOfA.getId()))
                .andExpect(jsonPath("$.notifications[0].productName").value("Leche de A"))
                .andExpect(jsonPath("$.notifications[0].category").value("EXPIRED"))
                .andExpect(jsonPath("$.notifications[0].severity").value("EXPIRED"))
                .andExpect(jsonPath("$.notifications[0].daysRemaining").value(-1))
                .andExpect(jsonPath("$.notifications[0].expirationDate").value(LocalDate.now().minusDays(1).toString()))
                .andExpect(jsonPath("$.notifications[0].read").value(false))
                .andExpect(jsonPath("$.notifications[1].productName").value("Yogur de A"))
                .andExpect(jsonPath("$.notifications[1].category").value("WITHIN_7_DAYS"))
                .andExpect(jsonPath("$.notifications[1].severity").value("UPCOMING"));
    }

    @Test
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/notifications"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/notifications/read-all"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void markAsReadPersistsAndLowersUnreadCount() throws Exception {
        mockMvc.perform(authorized(post("/notifications/" + expiredOfA.getId() + "/read"), userA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1))
                .andExpect(jsonPath("$.notifications[0].read").value(true))
                .andExpect(jsonPath("$.notifications[1].read").value(false));

        // El estado se conserva en una consulta posterior.
        mockMvc.perform(authorized(get("/notifications"), userA))
                .andExpect(jsonPath("$.unreadCount").value(1))
                .andExpect(jsonPath("$.notifications[0].read").value(true));

        // Marcar dos veces no duplica el registro.
        mockMvc.perform(authorized(post("/notifications/" + expiredOfA.getId() + "/read"), userA))
                .andExpect(status().isOk());
        assertEquals(1, notificationReadRepository.count());
    }

    @Test
    void markAllAsReadLeavesNothingUnread() throws Exception {
        mockMvc.perform(authorized(post("/notifications/read-all"), userA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0))
                .andExpect(jsonPath("$.notifications[0].read").value(true))
                .andExpect(jsonPath("$.notifications[1].read").value(true));

        assertEquals(2, notificationReadRepository.count());

        // No afecta a las notificaciones del otro usuario.
        mockMvc.perform(authorized(get("/notifications"), userB))
                .andExpect(jsonPath("$.unreadCount").value(1));
    }

    @Test
    void notificationBecomesUnreadWhenProductChangesCategory() throws Exception {
        mockMvc.perform(authorized(post("/notifications/" + soonOfA.getId() + "/read"), userA))
                .andExpect(jsonPath("$.unreadCount").value(1));

        // La fecha se acerca: pasa de "7 días" a "3 días".
        mockMvc.perform(authorized(put("/products/" + soonOfA.getId()), userA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Yogur de A", LocalDate.now().plusDays(2))))
                .andExpect(status().isOk());

        mockMvc.perform(authorized(get("/notifications"), userA))
                .andExpect(jsonPath("$.unreadCount").value(2))
                .andExpect(jsonPath("$.notifications[1].category").value("WITHIN_3_DAYS"))
                .andExpect(jsonPath("$.notifications[1].read").value(false));
    }

    @Test
    void notificationDisappearsWhenProductIsNoLongerNearExpiry() throws Exception {
        mockMvc.perform(authorized(put("/products/" + soonOfA.getId()), userA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Yogur de A", LocalDate.now().plusDays(90))))
                .andExpect(status().isOk());

        mockMvc.perform(authorized(get("/notifications"), userA))
                .andExpect(jsonPath("$.notifications.length()").value(1))
                .andExpect(jsonPath("$.notifications[0].productName").value("Leche de A"));
    }

    @Test
    void deletingProductRemovesItsNotificationAndReadState() throws Exception {
        mockMvc.perform(authorized(post("/notifications/" + expiredOfA.getId() + "/read"), userA))
                .andExpect(status().isOk());
        assertEquals(1, notificationReadRepository.count());

        mockMvc.perform(authorized(delete("/products/" + expiredOfA.getId()), userA))
                .andExpect(status().isNoContent());

        assertEquals(0, notificationReadRepository.count());
        mockMvc.perform(authorized(get("/notifications"), userA))
                .andExpect(jsonPath("$.notifications.length()").value(1))
                .andExpect(jsonPath("$.notifications[0].productName").value("Yogur de A"));
    }

    @Test
    void userCannotMarkNotificationOfAnotherUser() throws Exception {
        mockMvc.perform(authorized(post("/notifications/" + soonOfB.getId() + "/read"), userA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Producto no encontrado"));

        assertEquals(0, notificationReadRepository.count());
        mockMvc.perform(authorized(get("/notifications"), userB))
                .andExpect(jsonPath("$.unreadCount").value(1))
                .andExpect(jsonPath("$.notifications[0].read").value(false));
    }

    @Test
    void markingProductWithoutNotificationReturns404() throws Exception {
        mockMvc.perform(authorized(post("/notifications/" + farOfA.getId() + "/read"), userA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("El producto no tiene notificaciones"));
    }

    @Test
    void clientDateIsUsedAndValidated() throws Exception {
        // Para un cliente que todavía está en ayer, el producto vencido ayer "vence hoy".
        String yesterday = LocalDate.now().minusDays(1).toString();
        mockMvc.perform(authorized(get("/notifications?today=" + yesterday), userA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications[0].category").value("EXPIRES_TODAY"))
                .andExpect(jsonPath("$.notifications[0].daysRemaining").value(0));

        mockMvc.perform(authorized(get("/notifications?today=" + LocalDate.now().plusDays(5)), userA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("La fecha enviada no coincide con la fecha actual"));

        mockMvc.perform(authorized(get("/notifications?today=ayer"), userA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Valor inválido para el parámetro 'today'"));
    }

    @Test
    void existingExpiryRulesAreUnchanged() throws Exception {
        // Un producto que vence hoy sigue contando como vencido en los endpoints anteriores.
        saveProduct("Pan de A", userA, 0);

        mockMvc.perform(authorized(get("/products/expired"), userA))
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(authorized(get("/products/stats"), userA))
                .andExpect(jsonPath("$.expiredProducts").value(2));

        assertTrue(productRepository.findByUserId(userA.getId()).size() == 4);
    }

    private MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request, User user) {
        return request.header("Authorization", "Bearer " + jwtService.generateToken(user.getEmail(), user.getId(), user.getName()));
    }

    private String productJson(String name, LocalDate expirationDate) {
        return "{\"name\":\"" + name + "\",\"description\":\"Descripcion\",\"category\":\"Frescos\",\"quantity\":3,\"expirationDate\":\"" + expirationDate + "\"}";
    }

    private User saveUser(String name, String email) {
        return userRepository.save(User.builder()
                .name(name)
                .email(email)
                .password(passwordEncoder.encode("secreta123"))
                .build());
    }

    private Product saveProduct(String name, User owner, int daysFromToday) {
        return productRepository.save(Product.builder()
                .name(name)
                .description("Descripcion")
                .category("Frescos")
                .quantity(3)
                .expirationDate(LocalDate.now().plusDays(daysFromToday))
                .user(owner)
                .build());
    }
}
