package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.model.Product;
import com.example.demo.model.User;
import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.JwtService;
import jakarta.servlet.Filter;
import java.time.LocalDate;
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
class SecurityIntegrationTest {

    private static final String OTHER_SECRET = "otra-clave-distinta-para-firmar-0123456789";
    private static final String PRODUCT_JSON = """
            {"name":"Modificado","description":"Cambio","category":"Otra","quantity":99,"expirationDate":"2030-01-01"}
            """;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private MockMvc mockMvc;
    private User userA;
    private User userB;
    private Product productOfA;
    private Product productOfB;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();

        productRepository.deleteAll();
        userRepository.deleteAll();

        userA = saveUser("Usuario A", "a@example.com");
        userB = saveUser("Usuario B", "b@example.com");
        productOfA = saveProduct("Leche de A", userA);
        productOfB = saveProduct("Yogur de B", userB);
    }

    // --- Autenticación ---

    @Test
    void requestWithoutTokenReturns401WithJson() throws Exception {
        mockMvc.perform(get("/products"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("Autenticación requerida"));
    }

    @Test
    void malformedTokenReturns401() throws Exception {
        mockMvc.perform(get("/products").header("Authorization", "Bearer abc.def.ghi"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Token inválido"));
    }

    @Test
    void tokenSignedWithAnotherKeyReturns401() throws Exception {
        String token = new JwtService(OTHER_SECRET, 3600000)
                .generateToken(userA.getEmail(), userA.getId(), userA.getName());

        mockMvc.perform(get("/products").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Token inválido"));
    }

    @Test
    void expiredTokenReturns401() throws Exception {
        mockMvc.perform(authorized(get("/products"), expiredTokenFor(userA)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Token expirado"));
    }

    @Test
    void tokenOfDeletedUserReturns401() throws Exception {
        String token = jwtService.generateToken("ya-no-existe@example.com", 999L, "Fantasma");

        mockMvc.perform(authorized(get("/products"), token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Token inválido"));
    }

    @Test
    void validTokenAllowsAccess() throws Exception {
        mockMvc.perform(authorized(get("/products"), tokenFor(userA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Leche de A"));
    }

    @Test
    void loginWorksEvenWithInvalidTokenHeader() throws Exception {
        mockMvc.perform(post("/login")
                        .header("Authorization", "Bearer abc.def.ghi")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@example.com\",\"password\":\"secreta123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("a@example.com"));
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@example.com\",\"password\":\"incorrecta\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Email o contraseña incorrectos"));
    }

    @Test
    void validationErrorOfAuthenticatedUserIsNot401() throws Exception {
        mockMvc.perform(authorized(post("/products"), tokenFor(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedJsonReturns400WithJsonError() throws Exception {
        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{roto"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("El cuerpo de la petición no es un JSON válido"));
    }

    @Test
    void nonNumericDaysReturns400WithJsonError() throws Exception {
        mockMvc.perform(authorized(get("/products/expiring?days=abc"), tokenFor(userA)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Valor inválido para el parámetro 'days'"));
    }

    // --- Registro ---

    @Test
    void registerCreatesUserWithoutExposingPassword() throws Exception {
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nuevo\",\"email\":\"NUEVO@example.com\",\"password\":\"secreta123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("nuevo@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist());

        User saved = userRepository.findByEmail("nuevo@example.com").orElseThrow();
        assertTrue(passwordEncoder.matches("secreta123", saved.getPassword()));
    }

    @Test
    void registerWithExistingEmailReturns409() throws Exception {
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Otro\",\"email\":\"a@example.com\",\"password\":\"secreta123\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("El email ya se encuentra registrado"));
    }

    @Test
    void corsPreflightIsAllowedOnlyForConfiguredOrigins() throws Exception {
        mockMvc.perform(options("/products")
                        .header("Origin", "http://localhost:8081")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:8081"));

        mockMvc.perform(options("/products")
                        .header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    // --- Aislamiento entre usuarios ---

    @Test
    void userCannotReadProductOfAnotherUser() throws Exception {
        mockMvc.perform(authorized(get("/products/" + productOfB.getId()), tokenFor(userA)))
                .andExpect(status().isNotFound());
    }

    @Test
    void userCannotUpdateProductOfAnotherUser() throws Exception {
        mockMvc.perform(authorized(put("/products/" + productOfB.getId()), tokenFor(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PRODUCT_JSON))
                .andExpect(status().isNotFound());

        Product unchanged = productRepository.findById(productOfB.getId()).orElseThrow();
        assertEquals("Yogur de B", unchanged.getName());
        assertEquals(3, unchanged.getQuantity());
    }

    @Test
    void userCannotDeleteProductOfAnotherUser() throws Exception {
        mockMvc.perform(authorized(delete("/products/" + productOfB.getId()), tokenFor(userA)))
                .andExpect(status().isNotFound());

        assertTrue(productRepository.findById(productOfB.getId()).isPresent());
    }

    @Test
    void listsAndStatsOnlyIncludeOwnProducts() throws Exception {
        String tokenB = tokenFor(userB);

        mockMvc.perform(authorized(get("/products"), tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Yogur de B"));

        mockMvc.perform(authorized(get("/products/expiring?days=30"), tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(authorized(get("/products/stats"), tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProducts").value(1));
    }

    @Test
    void ownerCanUpdateAndDeleteOwnProduct() throws Exception {
        String tokenA = tokenFor(userA);

        mockMvc.perform(authorized(put("/products/" + productOfA.getId()), tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PRODUCT_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Modificado"));

        mockMvc.perform(authorized(delete("/products/" + productOfA.getId()), tokenA))
                .andExpect(status().isNoContent());

        assertTrue(productRepository.findById(productOfA.getId()).isEmpty());
    }

    private MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }

    private String tokenFor(User user) {
        return jwtService.generateToken(user.getEmail(), user.getId(), user.getName());
    }

    private String expiredTokenFor(User user) {
        return new JwtService(testSecret(), -1000)
                .generateToken(user.getEmail(), user.getId(), user.getName());
    }

    private String testSecret() {
        return context.getEnvironment().getProperty("app.jwt.secret");
    }

    private User saveUser(String name, String email) {
        return userRepository.save(User.builder()
                .name(name)
                .email(email)
                .password(passwordEncoder.encode("secreta123"))
                .build());
    }

    private Product saveProduct(String name, User owner) {
        return productRepository.save(Product.builder()
                .name(name)
                .description("Descripción")
                .category("Lácteos")
                .quantity(3)
                .expirationDate(LocalDate.now().plusDays(10))
                .user(owner)
                .build());
    }
}
