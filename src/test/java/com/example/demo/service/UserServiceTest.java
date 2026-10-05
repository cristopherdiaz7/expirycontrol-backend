package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.LoginResponse;
import com.example.demo.exception.InvalidCredentialsException;
import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.JwtService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private UserService userService;

    @Test
    void loginReturnsTokenAndUserForValidCredentials() {
        User user = User.builder()
                .id(1L)
                .name("Usuario Prueba")
                .email("usuario@example.com")
                .password("hashed-password")
                .build();
        LoginRequest request = new LoginRequest(" USUARIO@EXAMPLE.COM ", "123456");

        when(userRepository.findByEmail("usuario@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("123456", "hashed-password")).thenReturn(true);
        when(jwtService.generateToken("usuario@example.com", "Usuario Prueba"))
                .thenReturn("jwt-token");

        LoginResponse response = userService.login(request);

        assertEquals("jwt-token", response.getToken());
        assertEquals(1L, response.getUser().getId());
        assertEquals("usuario@example.com", response.getUser().getEmail());
        verify(passwordEncoder).matches("123456", "hashed-password");
    }

    @Test
    void loginThrowsExceptionForInvalidPassword() {
        User user = User.builder()
                .email("usuario@example.com")
                .password("hashed-password")
                .build();
        LoginRequest request = new LoginRequest("usuario@example.com", "incorrecta");

        when(userRepository.findByEmail("usuario@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("incorrecta", "hashed-password")).thenReturn(false);

        InvalidCredentialsException exception = assertThrows(
                InvalidCredentialsException.class,
                () -> userService.login(request));

        assertEquals("Email o contraseña incorrectos", exception.getMessage());
    }
}