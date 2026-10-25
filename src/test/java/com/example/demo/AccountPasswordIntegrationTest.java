package com.example.demo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

// Cambio de contraseña de punta a punta: seguridad, validaciones y login con la contraseña nueva.
// saveUser crea los usuarios con la contraseña "secreta123".
class AccountPasswordIntegrationTest extends IntegrationTestSupport {

    private User user;
    private User otherUser;

    @BeforeEach
    void setUp() {
        user = saveUser("Usuario", "usuario@example.com");
        otherUser = saveUser("Otro", "otro@example.com");
    }

    private ResultActions changePassword(User owner, String current, String next) throws Exception {
        return mockMvc.perform(authorized(put("/account/password"), owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}"));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    @Test
    void requiresToken() throws Exception {
        mockMvc.perform(put("/account/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"secreta123\",\"newPassword\":\"nueva456\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changesOnlyTheOwnPasswordAndTheNewOneWorks() throws Exception {
        changePassword(user, "secreta123", "nueva456").andExpect(status().isNoContent());

        login("usuario@example.com", "nueva456").andExpect(status().isOk());
        login("usuario@example.com", "secreta123").andExpect(status().isUnauthorized());
        // El otro usuario conserva su contraseña.
        login("otro@example.com", "secreta123").andExpect(status().isOk());
    }

    @Test
    void wrongCurrentPasswordReturns400AndKeepsThePassword() throws Exception {
        changePassword(user, "equivocada", "nueva456")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.currentPassword").value("La contraseña actual es incorrecta"));

        login("usuario@example.com", "secreta123").andExpect(status().isOk());
    }

    @Test
    void shortNewPasswordReturns400() throws Exception {
        changePassword(user, "secreta123", "123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.newPassword").value("La contraseña debe tener al menos 6 caracteres"));
    }

    @Test
    void samePasswordReturns400() throws Exception {
        changePassword(otherUser, "secreta123", "secreta123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.newPassword").value("La nueva contraseña debe ser distinta de la actual"));
    }
}
