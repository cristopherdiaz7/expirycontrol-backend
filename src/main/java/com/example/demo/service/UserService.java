package com.example.demo.service;

import com.example.demo.dto.UserRegisterRequest;
import com.example.demo.dto.UserResponse;
import com.example.demo.exception.EmailAlreadyExistsException;
import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse register(UserRegisterRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyExistsException("El email ya se encuentra registrado");
        }

        String hashedPassword = passwordEncoder.encode(request.getPassword());

        User user = User.builder()
                .name(request.getName().trim())
                .email(normalizedEmail)
                .password(hashedPassword)
                .build();

        User savedUser = userRepository.save(user);

        return UserResponse.builder()
                .id(savedUser.getId())
                .name(savedUser.getName())
                .email(savedUser.getEmail())
                .build();
    }
}
