package com.santsg.tourvisio.controller;

import com.santsg.tourvisio.config.JwtProvider;
import com.santsg.tourvisio.dto.auth.LoginRequest;
import com.santsg.tourvisio.dto.auth.LoginResponse;
import com.santsg.tourvisio.dto.auth.SignupRequest;
import com.santsg.tourvisio.dto.auth.UserResponse;
import com.santsg.tourvisio.entity.User;
import com.santsg.tourvisio.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "User Authentication", description = "Endpoints for user signup and login")
@Slf4j
public class AuthController {

    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;

    public AuthController(UserRepository userRepository, JwtProvider jwtProvider) {
        this.userRepository = userRepository;
        this.jwtProvider = jwtProvider;
    }

    @PostMapping(value = "/signup", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "User Signup", description = "Register a new user in the system")
    public ResponseEntity<?> signup(@Valid @RequestBody SignupRequest request) {
        log.info("[AuthController] Signup request received for email={}", request.getEmail());

        // Validate password confirmation match
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            log.warn("[AuthController] Signup failed: passwords do not match for email={}", request.getEmail());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Bad Request",
                    "message", "Password and confirmPassword do not match"
            ));
        }

        // Validate email uniqueness
        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("[AuthController] Signup failed: email={} already exists", request.getEmail());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", "Conflict",
                    "message", "Email already exists"
            ));
        }

        // Hash the password using BCrypt
        String hashedPassword = BCrypt.hashpw(request.getPassword(), BCrypt.gensalt());

        // Map and save the user
        User user = User.builder()
                .firstName(request.getName())
                .lastName(request.getLastname())
                .email(request.getEmail())
                .phone(request.getPhone())
                .password(hashedPassword)
                .build();

        User savedUser = userRepository.save(user);
        log.info("[AuthController] User registered successfully with id={}", savedUser.getId());

        // Build UserResponse (no password field)
        UserResponse userResponse = UserResponse.builder()
                .firstName(savedUser.getFirstName())
                .lastName(savedUser.getLastName())
                .email(savedUser.getEmail())
                .phone(savedUser.getPhone())
                .country(savedUser.getCountry())
                .gender(savedUser.getGender())
                .dateOfBirth(savedUser.getDateOfBirth())
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(userResponse);
    }

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "User Login", description = "Authenticate credentials and generate a JWT token")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        log.info("[AuthController] Login request received for email={}", request.getEmail());

        // Find user by email
        User user = userRepository.findByEmail(request.getEmail()).orElse(null);
        if (user == null) {
            log.warn("[AuthController] Login failed: user not found for email={}", request.getEmail());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", "Unauthorized",
                    "message", "Invalid email or password"
            ));
        }

        // Verify password hash
        if (!BCrypt.checkpw(request.getPassword(), user.getPassword())) {
            log.warn("[AuthController] Login failed: incorrect password for email={}", request.getEmail());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", "Unauthorized",
                    "message", "Invalid email or password"
            ));
        }

        // Generate JWT token
        String token = jwtProvider.generateToken(user.getId(), user.getEmail());
        log.info("[AuthController] Login successful for email={}, generated JWT", request.getEmail());

        // Build response payload
        UserResponse userResponse = UserResponse.builder()
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .country(user.getCountry())
                .gender(user.getGender())
                .dateOfBirth(user.getDateOfBirth())
                .build();

        LoginResponse loginResponse = LoginResponse.builder()
                .token(token)
                .user(userResponse)
                .build();

        return ResponseEntity.ok(loginResponse);
    }
}
