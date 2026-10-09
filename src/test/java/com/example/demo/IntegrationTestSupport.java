package com.example.demo;

import com.example.demo.model.Product;
import com.example.demo.model.User;
import com.example.demo.repository.LossRepository;
import com.example.demo.repository.NotificationReadRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.JwtService;
import jakarta.servlet.Filter;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

// Base de los tests de integración: aplicación completa sobre H2, peticiones HTTP con la cadena
// de seguridad real y base de datos vacía antes y después de cada test.
@SpringBootTest
@ActiveProfiles("test")
abstract class IntegrationTestSupport {

    @Autowired
    protected WebApplicationContext context;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected ProductRepository productRepository;

    @Autowired
    protected LossRepository lossRepository;

    @Autowired
    protected NotificationReadRepository notificationReadRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected JwtService jwtService;

    protected MockMvc mockMvc;

    @BeforeEach
    void prepareMockMvcAndDatabase() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
        cleanDatabase();
    }

    @AfterEach
    void cleanDatabase() {
        lossRepository.deleteAll();
        notificationReadRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
    }

    protected MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }

    protected MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request, User user) {
        return authorized(request, tokenFor(user));
    }

    protected String tokenFor(User user) {
        return jwtService.generateToken(user.getEmail(), user.getId(), user.getName());
    }

    protected User saveUser(String name, String email) {
        return userRepository.save(User.builder()
                .name(name)
                .email(email)
                .password(passwordEncoder.encode("secreta123"))
                .build());
    }

    // unitPrice null: producto anterior a la existencia del precio.
    protected Product saveProduct(String name, User owner, int daysFromToday, int quantity, String unitPrice) {
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

    protected Product saveProduct(String name, User owner, int daysFromToday) {
        return saveProduct(name, owner, daysFromToday, 3, null);
    }

    protected Product saveProduct(String name, User owner) {
        return saveProduct(name, owner, 10);
    }
}
