package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.model.Product;
import com.example.demo.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class ProductApiIntegrationTest extends IntegrationTestSupport {

    private static final String VALID_PRODUCT = """
            {"name":"  Leche  ","description":"Entera","category":"Lácteos","quantity":12,"expirationDate":"2030-01-15","unitPrice":1500.50}
            """;

    private User user;
    private String token;

    @BeforeEach
    void setUp() {
        user = saveUser("Usuario", "usuario@example.com");
        token = tokenFor(user);
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
        Product product = saveProduct("Yogur", user);

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
        Product product = saveProduct("Yogur", user);

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
        return authorized(request, token);
    }
}
