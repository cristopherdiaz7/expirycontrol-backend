package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.LoginResponse;
import com.example.demo.dto.UserRegisterRequest;
import com.example.demo.dto.UserResponse;
import com.example.demo.exception.EmailAlreadyExistsException;
import com.example.demo.exception.InvalidCredentialsException;
import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.JwtService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
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
        when(jwtService.generateToken("usuario@example.com", 1L, "Usuario Prueba"))
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

    @Test
    void loginThrowsSameExceptionForUnknownEmail() {
        LoginRequest request = new LoginRequest("nadie@example.com", "123456");

        when(userRepository.findByEmail("nadie@example.com")).thenReturn(Optional.empty());

        InvalidCredentialsException exception = assertThrows(
                InvalidCredentialsException.class,
                () -> userService.login(request));

        assertEquals("Email o contraseña incorrectos", exception.getMessage());
        verify(passwordEncoder, never()).matches(any(), any());
    }

    // --- Registro ---

    @Test
    void registerNormalizesEmailAndStoresHashedPassword() {
        UserRegisterRequest request = new UserRegisterRequest("  Usuario Nuevo  ", " NUEVO@Example.COM ", "secreta123");

        when(userRepository.existsByEmail("nuevo@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secreta123")).thenReturn("hash-bcrypt");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(5L);
            return saved;
        });

        UserResponse response = userService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        assertEquals("Usuario Nuevo", captor.getValue().getName());
        assertEquals("nuevo@example.com", captor.getValue().getEmail());
        assertEquals("hash-bcrypt", captor.getValue().getPassword());

        assertEquals(5L, response.getId());
        assertEquals("Usuario Nuevo", response.getName());
        assertEquals("nuevo@example.com", response.getEmail());
    }

    @Test
    void registerRejectsExistingEmailWithoutSaving() {
        UserRegisterRequest request = new UserRegisterRequest("Otro", "Usuario@Example.com", "secreta123");

        when(userRepository.existsByEmail("usuario@example.com")).thenReturn(true);

        EmailAlreadyExistsException exception = assertThrows(
                EmailAlreadyExistsException.class,
                () -> userService.register(request));

        assertEquals("El email ya se encuentra registrado", exception.getMessage());
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void registerRejectsEmailTakenByConcurrentRequest() {
        UserRegisterRequest request = new UserRegisterRequest("Otro", "usuario@example.com", "secreta123");

        when(userRepository.existsByEmail("usuario@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secreta123")).thenReturn("hash-bcrypt");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("email duplicado"));

        assertThrows(EmailAlreadyExistsException.class, () -> userService.register(request));
    }
}