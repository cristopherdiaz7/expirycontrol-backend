package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class ProductApiIntegrationTest {

    private static final String VALID_PRODUCT = """
            {"name":"  Leche  ","description":"Entera","category":"Lácteos","quantity":12,"expirationDate":"2030-01-15","unitPrice":1500.50}
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
    private User user;
    private String token;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();

        productRepository.deleteAll();
        userRepository.deleteAll();

        user = userRepository.save(User.builder()
                .name("Usuario")
                .email("usuario@example.com")
                .password(passwordEncoder.encode("secreta123"))
                .build());
        token = jwtService.generateToken(user.getEmail(), user.getId(), user.getName());
    }

    // --- CRUD ---

    @Test
    void createReturns201WithProduct() throws Exception {
        mockMvc.perform(authorized(post("/products")).contentType(MediaType.APPLICATION_JSON).content(VALID_PRODUCT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Leche"))
                .andExpect(jsonPath("$.description").value("Entera"))
                .andExpect(jsonPath("$.category").value("Lácteos"))
                .andExpect(jsonPath("$.quantity").value(12))
                .andExpect(jsonPath("$.expirationDate").value("2030-01-15"))
                .andExpect(jsonPath("$.unitPrice").value(1500.50));

        assertEquals(1, productRepository.findByUserId(user.getId()).size());
    }

    @Test
    void getByIdReturns200ForOwnProduct() throws Exception {
        Product product = saveProduct("Yogur");

        mockMvc.perform(authorized(get("/products/" + product.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(product.getId()))
                .andExpect(jsonPath("$.name").value("Yogur"));
    }

    @Test
    void unknownIdReturns404WithJson() throws Exception {
        mockMvc.perform(authorized(get("/products/999999")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Producto no encontrado"));

        mockMvc.perform(authorized(put("/products/999999")).contentType(MediaType.APPLICATION_JSON).content(VALID_PRODUCT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Producto no encontrado"));
    }

    @Test
    void nonNumericIdReturns400WithJson() throws Exception {
        mockMvc.perform(authorized(get("/products/abc")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Valor inválido para el parámetro 'id'"));
    }

    // --- Validación de productos ---

    @Test
    void productWithoutNameReturns400WithFieldDetail() throws Exception {
        expectProductValidationError(
                "{\"name\":\" \",\"description\":\"Entera\",\"category\":\"Lácteos\",\"quantity\":1,\"expirationDate\":\"2030-01-15\"}",
                "name", "El nombre es obligatorio");
    }

    @Test
    void productWithoutCategoryReturns400WithFieldDetail() throws Exception {
        expectProductValidationError(
                "{\"name\":\"Leche\",\"description\":\"Entera\",\"quantity\":1,\"expirationDate\":\"2030-01-15\"}",
                "category", "La categoría es obligatoria");
    }

    @Test
    void productWithNegativeQuantityReturns400WithFieldDetail() throws Exception {
        expectProductValidationError(
                "{\"name\":\"Leche\",\"description\":\"Entera\",\"category\":\"Lácteos\",\"quantity\":-1,\"expirationDate\":\"2030-01-15\"}",
                "quantity", "La cantidad no puede ser negativa");
    }

    @Test
    void productWithoutDateReturns400WithFieldDetail() throws Exception {
        expectProductValidationError(
                "{\"name\":\"Leche\",\"description\":\"Entera\",\"category\":\"Lácteos\",\"quantity\":1}",
                "expirationDate", "La fecha de vencimiento es obligatoria");
    }

    @Test
    void invalidProductOnUpdateReturns400AndKeepsData() throws Exception {
        Product product = saveProduct("Yogur");

        mockMvc.perform(authorized(put("/products/" + product.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"description\":\"x\",\"category\":\"y\",\"quantity\":1,\"expirationDate\":\"2030-01-15\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.name").value("El nombre es obligatorio"));

        assertEquals("Yogur", productRepository.findById(product.getId()).orElseThrow().getName());
    }

    // --- Validación de registro ---

    @Test
    void registerWithInvalidEmailReturns400WithFieldDetail() throws Exception {
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nuevo\",\"email\":\"no-es-un-email\",\"password\":\"secreta123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Datos de entrada inválidos o faltantes"))
                .andExpect(jsonPath("$.details.email").value("El email debe ser válido"));
    }

    @Test
    void registerWithShortPasswordReturns400WithFieldDetail() throws Exception {
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nuevo\",\"email\":\"nuevo@example.com\",\"password\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.password").value("La contraseña debe tener al menos 6 caracteres"));

        assertEquals(1, userRepository.count());
    }

    private void expectProductValidationError(String body, String field, String message) throws Exception {
        mockMvc.perform(authorized(post("/products")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Datos de entrada inválidos o faltantes"))
                .andExpect(jsonPath("$.details." + field).value(message));

        assertEquals(0, productRepository.count());
    }

    private MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + token);
    }

    private Product saveProduct(String name) {
        return productRepository.save(Product.builder()
                .name(name)
                .description("Descripción")
                .category("Lácteos")
                .quantity(3)
                .expirationDate(LocalDate.now().plusDays(10))
                .user(user)
                .build());
    }
}
