package com.santsg.tourvisio.controller;

import com.santsg.tourvisio.config.ActiveTokenRegistry;
import com.santsg.tourvisio.config.JwtProvider;
import com.santsg.tourvisio.dto.auth.AdminLoginRequest;
import com.santsg.tourvisio.dto.auth.LoginRequest;
import com.santsg.tourvisio.dto.auth.LoginResponse;
import com.santsg.tourvisio.dto.auth.SignupRequest;
import com.santsg.tourvisio.dto.auth.UserResponse;
import com.santsg.tourvisio.entity.User;
import com.santsg.tourvisio.repository.UserRepository;

import com.santsg.tourvisio.dto.auth.OAuthLoginRequest;
import com.santsg.tourvisio.service.OAuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.extern.slf4j.Slf4j;

import org.mindrot.jbcrypt.BCrypt;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
@Tag(name = "User Authentication", description = "Endpoints for user signup, login, google oauth and admin login")
@Slf4j
public class AuthController {

    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final ActiveTokenRegistry tokenRegistry;
    private final OAuthService oAuthService;

    // Admin şifresi
    private final String correctAdminPassword = "admin2026";

    public AuthController(
            UserRepository userRepository,
            JwtProvider jwtProvider,
            ActiveTokenRegistry tokenRegistry,
            OAuthService oAuthService) {
        this.userRepository = userRepository;
        this.jwtProvider = jwtProvider;
        this.tokenRegistry = tokenRegistry;
        this.oAuthService = oAuthService;
    }

    // =========================================================
    // SIGNUP
    // =========================================================

    @PostMapping(value = "/signup", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "User Signup", description = "Register a new user in the system")
    public ResponseEntity<?> signup(
            @Valid @RequestBody SignupRequest request) {

        log.info(
                "[AuthController] Signup request received for email={}",
                request.getEmail());

        // Password confirmation kontrolü
        if (!request.getPassword()
                .equals(request.getConfirmPassword())) {

            log.warn(
                    "[AuthController] Signup failed: passwords do not match for email={}",
                    request.getEmail());

            return ResponseEntity
                    .badRequest()
                    .body(
                            Map.of(
                                    "error", "Bad Request",
                                    "message",
                                    "Password and confirmPassword do not match"));
        }

        // Email ve telefon benzersizliği
        boolean emailExists = userRepository.existsByEmail(
                request.getEmail());

        boolean phoneExists = userRepository.existsByPhone(
                request.getPhone());

        if (emailExists || phoneExists) {

            java.util.List<String> messages = new java.util.ArrayList<>();

            java.util.List<String> fields = new java.util.ArrayList<>();

            if (emailExists) {

                log.warn(
                        "[AuthController] Signup failed: email={} already exists",
                        request.getEmail());

                messages.add(
                        "Email already exists");

                fields.add(
                        "email");
            }

            if (phoneExists) {

                log.warn(
                        "[AuthController] Signup failed: phone={} already exists",
                        request.getPhone());

                messages.add(
                        "Phone number already exists");

                fields.add(
                        "phone");
            }

            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(
                            Map.of(
                                    "error", "Conflict",
                                    "message",
                                    String.join("; ", messages),
                                    "fields", fields));
        }

        // Şifre hash
        String hashedPassword = BCrypt.hashpw(
                request.getPassword(),
                BCrypt.gensalt());

        // Kullanıcı oluştur
        User user = User.builder()
                .firstName(request.getName())
                .lastName(request.getLastname())
                .email(request.getEmail())
                .phone(request.getPhone())
                .password(hashedPassword)
                .build();

        User savedUser = userRepository.save(user);

        log.info(
                "[AuthController] User registered successfully with id={}",
                savedUser.getId());

        UserResponse userResponse = UserResponse.builder()
                .firstName(savedUser.getFirstName())
                .lastName(savedUser.getLastName())
                .email(savedUser.getEmail())
                .phone(savedUser.getPhone())
                .country(savedUser.getCountry())
                .gender(savedUser.getGender())
                .dateOfBirth(savedUser.getDateOfBirth())
                .build();

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(userResponse);
    }

    // =========================================================
    // LOGIN
    // =========================================================

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "User Login", description = "Authenticate credentials and generate a JWT token")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest request) {

        log.info(
                "[AuthController] Login request received for email={}",
                request.getEmail());

        // Kullanıcıyı bul
        User user = userRepository
                .findByEmail(
                        request.getEmail())
                .orElse(null);

        if (user == null) {

            log.warn(
                    "[AuthController] Login failed: user not found for email={}",
                    request.getEmail());

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(
                            Map.of(
                                    "error", "Unauthorized",
                                    "message",
                                    "Invalid email or password"));
        }

        // Şifreyi doğrula
        if (!BCrypt.checkpw(
                request.getPassword(),
                user.getPassword())) {

            log.warn(
                    "[AuthController] Login failed: incorrect password for email={}",
                    request.getEmail());

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(
                            Map.of(
                                    "error", "Unauthorized",
                                    "message",
                                    "Invalid email or password"));
        }

        // =====================================================
        // JWT TOKEN OLUŞTUR
        // =====================================================

        String token = jwtProvider.generateToken(
                user.getId(),
                user.getEmail());

        // =====================================================
        // TOKEN'I ACTIVE TOKEN REGISTRY'YE KULLANICIYLA KAYDET
        // =====================================================

        tokenRegistry.registerToken(
                token,
                user.getId(),
                user.getEmail());

        log.info(
                "[AuthController] Login successful for email={}, userId={}, generated JWT and registered active token",
                user.getEmail(),
                user.getId());

        // Response
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

        return ResponseEntity.ok(
                loginResponse);
    }

    // =========================================================
    // ADMIN LOGIN
    // =========================================================

    @PostMapping(value = "/admin-login", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Admin Login", description = "Authenticate admin password and provide a temporary token")
    public ResponseEntity<?> adminLogin(
            @Valid @RequestBody AdminLoginRequest request) {

        log.info(
                "[AuthController] Admin login attempt received");

        if (request.getPassword() == null
                || request.getPassword()
                        .trim()
                        .isEmpty()) {

            log.warn(
                    "[AuthController] Admin login failed: Password field is empty");

            return ResponseEntity
                    .badRequest()
                    .body(
                            Map.of(
                                    "error", "Bad Request",
                                    "message",
                                    "Password cannot be empty"));
        }

        if (request.getPassword()
                .equals(correctAdminPassword)) {

            log.info(
                    "[AuthController] Admin login successful!");

            String adminToken = "sanny-admin-secure-jwt-token-2026";

            return ResponseEntity.ok(
                    Map.of(
                            "success", true,
                            "token", adminToken,
                            "message",
                            "Admin login successful"));

        } else {

            log.warn(
                    "[AuthController] Admin login failed: Invalid admin password");

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(
                            Map.of(
                                    "error", "Unauthorized",
                                    "message",
                                    "Invalid admin password"));
        }
    }

    // =========================================================
    // OAUTH LOGIN (Google)
    // =========================================================

    @PostMapping(value = "/oauth-login", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "OAuth Login", description = "Login or signup using Google OAuth ID token")
    public ResponseEntity<?> oauthLogin(@Valid @RequestBody OAuthLoginRequest request) {
        log.info("[AuthController] OAuth login request received for provider={}", request.getProvider());
        try {
            Map<String, String> googleUserInfo = oAuthService.verifyGoogleToken(request.getIdToken());
            String email = googleUserInfo.get("email");
            String firstName = googleUserInfo.get("firstName");
            String lastName = googleUserInfo.get("lastName");

            User user = oAuthService.findOrCreateUser(email, firstName, lastName, request.getProvider());

            String token = jwtProvider.generateToken(user.getId(), user.getEmail());
            tokenRegistry.registerToken(token, user.getId(), user.getEmail());

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

            log.info("[AuthController] OAuth login successful for email={}, userId={}", user.getEmail(), user.getId());
            return ResponseEntity.ok(loginResponse);
        } catch (Exception e) {
            log.error("[AuthController] OAuth login failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized", "message", "Google verification failed: " + e.getMessage()));
        }
    }
}