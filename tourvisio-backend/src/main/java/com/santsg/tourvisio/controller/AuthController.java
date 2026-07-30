package com.santsg.tourvisio.controller;

import com.santsg.tourvisio.config.JwtProvider;
import com.santsg.tourvisio.dto.auth.LoginRequest;
import com.santsg.tourvisio.dto.auth.LoginResponse;
import com.santsg.tourvisio.dto.auth.OAuthLoginRequest;
import com.santsg.tourvisio.dto.auth.SignupRequest;
import com.santsg.tourvisio.dto.auth.UserResponse;
import com.santsg.tourvisio.dto.auth.AdminLoginRequest;
import com.santsg.tourvisio.entity.User;
import com.santsg.tourvisio.repository.UserRepository;
import com.santsg.tourvisio.service.OAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*") // CORS hatasını önlemek için eklendi!
@Tag(name = "User Authentication", description = "Endpoints for user signup, login and admin login")
@Slf4j
public class AuthController {

    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final OAuthService oAuthService;
    private final PasswordEncoder passwordEncoder;
    private final com.santsg.tourvisio.service.EmailService emailService;

    // In-Memory store for Email OTP codes: Key = email, Value = OtpDetails
    private static final java.util.concurrent.ConcurrentHashMap<String, OtpEntry> emailOtpStore = new java.util.concurrent.ConcurrentHashMap<>();

    private static class OtpEntry {
        String code;
        long expiryTime;

        OtpEntry(String code, long expiryTime) {
            this.code = code;
            this.expiryTime = expiryTime;
        }

        boolean isValid(String inputCode) {
            return System.currentTimeMillis() <= expiryTime && code.equals(inputCode);
        }
    }

    // In-Memory store for 2FA verification: Key = tempToken, Value = VerificationInfo
    private static final java.util.concurrent.ConcurrentHashMap<String, VerificationInfo> tempStore = new java.util.concurrent.ConcurrentHashMap<>();

    private static class VerificationInfo {
        String email;
        String code;
        long expiryTime;

        VerificationInfo(String email, String code, long expiryTime) {
            this.email = email;
            this.code = code;
            this.expiryTime = expiryTime;
        }

        boolean isValid(String inputCode) {
            return System.currentTimeMillis() <= expiryTime && code.equals(inputCode);
        }
    }

    // application.properties → sanny.admin.password (env: ADMIN_PASSWORD) değerini okur
    @Value("${sanny.admin.password}")
    private String correctAdminPassword;

    public AuthController(UserRepository userRepository, JwtProvider jwtProvider, OAuthService oAuthService, PasswordEncoder passwordEncoder, com.santsg.tourvisio.service.EmailService emailService) {
        this.userRepository = userRepository;
        this.jwtProvider = jwtProvider;
        this.oAuthService = oAuthService;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    @PostMapping(value = "/signup", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "User Signup", description = "Register a new user in the system")
    public ResponseEntity<?> signup(@Valid @RequestBody SignupRequest request) {
        log.info("[AuthController] Signup request received");

        // Validate password confirmation match
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            log.warn("[AuthController] Signup failed: passwords do not match");
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Bad Request",
                    "message", "Password and confirmPassword do not match"
            ));
        }

        // Validate email and phone uniqueness
        boolean emailExists = userRepository.existsByEmail(request.getEmail());
        boolean phoneExists = userRepository.existsByPhone(request.getPhone());

        if (emailExists || phoneExists) {
            java.util.List<String> messages = new java.util.ArrayList<>();
            java.util.List<String> fields = new java.util.ArrayList<>();
            if (emailExists) {
                log.warn("[AuthController] Signup failed: email already exists");
                messages.add("Email already exists");
                fields.add("email");
            }
            if (phoneExists) {
                log.warn("[AuthController] Signup failed: phone number already exists");
                messages.add("Phone number already exists");
                fields.add("phone");
            }
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", "Conflict",
                    "message", String.join("; ", messages),
                    "fields", fields
            ));
        }

        // Hash the password using PasswordEncoder (BCrypt)
        String hashedPassword = passwordEncoder.encode(request.getPassword());

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
                .createdAt(savedUser.getCreatedAt())
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(userResponse);
    }

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "User Login", description = "Authenticate credentials and generate a JWT token")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        log.info("[AuthController] Login request received");

        // Find user by email
        User user = userRepository.findByEmail(request.getEmail()).orElse(null);
        if (user == null) {
            log.warn("[AuthController] Login failed: user not found");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", "Unauthorized",
                    "message", "Invalid email or password"
            ));
        }

        // Verify password hash using PasswordEncoder matches
        if (user.getPassword() == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("[AuthController] Login failed: incorrect password");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", "Unauthorized",
                    "message", "Invalid email or password"
            ));
        }

        // Verify active status
        if (Boolean.FALSE.equals(user.getIsActive())) {
            log.warn("[AuthController] Login blocked: User account is inactive/restricted");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "error", "Forbidden",
                    "code", "ACCOUNT_RESTRICTED",
                    "message", "Hesabınız yönetici tarafından kısıtlanmıştır."
            ));
        }

        // Verify two-factor active
        if (Boolean.TRUE.equals(user.getIsTwoFactorEnabled())) {
            String code = String.format("%06d", new java.util.Random().nextInt(1000000));
            String tempToken = java.util.UUID.randomUUID().toString();
            
            // Store details for verification with 5 mins validity (300,000 ms)
            tempStore.put(tempToken, new VerificationInfo(user.getEmail(), code, System.currentTimeMillis() + 300000));
            
            // Send OTP email
            emailService.sendOtpEmail(user.getEmail(), code);
            
            log.info("[AuthController] Login triggers 2FA for userId={}, tempToken={}", user.getId(), tempToken);
            return ResponseEntity.ok(Map.of(
                    "twoFactorRequired", true,
                    "tempToken", tempToken,
                    "message", "İki adımlı doğrulama kodu e-posta adresinize gönderildi."
            ));
        }

        // Generate JWT token
        String token = jwtProvider.generateToken(user.getId(), user.getEmail());
        log.info("[AuthController] Login successful for userId={}, generated JWT", user.getId());

        // Build response payload
        UserResponse userResponse = UserResponse.builder()
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .country(user.getCountry())
                .gender(user.getGender())
                .dateOfBirth(user.getDateOfBirth())
                .createdAt(user.getCreatedAt())
                .isEmailVerified(Boolean.TRUE.equals(user.getIsEmailVerified()))
                .isPhoneVerified(Boolean.TRUE.equals(user.getIsPhoneVerified()))
                .build();

        LoginResponse loginResponse = LoginResponse.builder()
                .token(token)
                .user(userResponse)
                .build();

        return ResponseEntity.ok(loginResponse);
    }

    @PostMapping(value = {"/oauth-login", "/google", "/google-login", "/oauth"}, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "OAuth Social Login", description = "Authenticate via Google OAuth2 ID token")
    public ResponseEntity<?> oauthLogin(
            @RequestHeader(value = org.springframework.http.HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @RequestBody(required = false) @Valid OAuthLoginRequest request
    ) {
        String provider = (request != null && request.getProvider() != null) ? request.getProvider().toLowerCase() : "google";
        String idToken = (request != null && request.getIdToken() != null && !request.getIdToken().isBlank())
                ? request.getIdToken()
                : null;

        // Extract from Authorization header if missing from body
        if ((idToken == null || idToken.isBlank()) && authHeader != null && authHeader.startsWith("Bearer ")) {
            idToken = authHeader.substring(7).trim();
        }

        if (idToken == null || idToken.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Bad Request",
                    "message", "Google ID token is required either in request body (idToken) or Authorization header"
            ));
        }

        log.info("[AuthController] OAuth login request received for provider={}", provider);

        if (!"google".equals(provider)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Bad Request",
                    "message", "Unsupported OAuth provider: " + provider
            ));
        }

        try {
            Map<String, String> userInfo = oAuthService.verifyGoogleToken(idToken);

            // Find or create user
            User user = oAuthService.findOrCreateUser(
                    userInfo.get("email"),
                    userInfo.get("firstName"),
                    userInfo.get("lastName"),
                    provider
            );

            if (Boolean.FALSE.equals(user.getIsActive())) {
                log.warn("[AuthController] OAuth login blocked: User account is inactive/restricted");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                        "error", "Forbidden",
                        "code", "ACCOUNT_RESTRICTED",
                        "message", "Hesabınız yönetici tarafından kısıtlanmıştır."
                ));
            }

            // Verify two-factor active
            if (Boolean.TRUE.equals(user.getIsTwoFactorEnabled())) {
                String code = String.format("%06d", new java.util.Random().nextInt(1000000));
                String tempToken = java.util.UUID.randomUUID().toString();
                
                // Store details for verification with 5 mins validity (300,000 ms)
                tempStore.put(tempToken, new VerificationInfo(user.getEmail(), code, System.currentTimeMillis() + 300000));
                
                // Send OTP email
                emailService.sendOtpEmail(user.getEmail(), code);
                
                log.info("[AuthController] OAuth triggers 2FA for userId={}, tempToken={}", user.getId(), tempToken);
                return ResponseEntity.ok(Map.of(
                        "twoFactorRequired", true,
                        "tempToken", tempToken,
                        "message", "İki adımlı doğrulama kodu e-posta adresinize gönderildi."
                ));
            }

            // Generate JWT token
            String token = jwtProvider.generateToken(user.getId(), user.getEmail());
            log.info("[AuthController] OAuth login successful for userId={}, provider={}", user.getId(), provider);

            // Build response payload (same format as regular login)
            UserResponse userResponse = UserResponse.builder()
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .email(user.getEmail())
                    .phone(user.getPhone())
                    .country(user.getCountry())
                    .gender(user.getGender())
                    .dateOfBirth(user.getDateOfBirth())
                    .createdAt(user.getCreatedAt())
                    .build();

            LoginResponse loginResponse = LoginResponse.builder()
                    .token(token)
                    .user(userResponse)
                    .build();

            return ResponseEntity.ok(loginResponse);

        } catch (IllegalStateException e) {
            log.error("[AuthController] OAuth configuration error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "error", "Service Unavailable",
                    "message", e.getMessage()
            ));
        } catch (SecurityException e) {
            log.warn("[AuthController] OAuth token verification failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", "Unauthorized",
                    "message", "OAuth token verification failed: " + e.getMessage()
            ));
        } catch (Exception e) {
            log.error("[AuthController] OAuth login error: {}", e.getMessage(), e);
            String detail = (e.getMessage() != null && !e.getMessage().isBlank()) ? e.getMessage() : e.getClass().getSimpleName();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Internal Server Error",
                    "message", "OAuth login error: " + detail
            ));
        }
    }

    @PostMapping(value = "/admin-login", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Admin Login", description = "Authenticate admin password and provide a temporary token")
    public ResponseEntity<?> adminLogin(@Valid @RequestBody AdminLoginRequest request) {
        log.info("[AuthController] Admin login attempt received");

        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            log.warn("[AuthController] Admin login failed: Password field is empty");
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Bad Request",
                    "message", "Password cannot be empty"
            ));
        }

        // Check if the provided password matches the application.properties password
        if (request.getPassword().equals(correctAdminPassword)) {
            log.info("[AuthController] Admin login successful!");
            
            // Temporary token for frontend
            String adminToken = "sanny-admin-secure-jwt-token-2026"; 

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "token", adminToken,
                    "message", "Admin login successful"
            ));
        } else {
            log.warn("[AuthController] Admin login failed: Invalid admin password");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", "Unauthorized",
                    "message", "Invalid admin password"
            ));
        }
    }

    @PostMapping(value = "/send-email-otp", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Send Email OTP", description = "Generates and sends 6-digit OTP code to email via JavaMailSender")
    public ResponseEntity<?> sendEmailOtp(@RequestBody(required = false) Map<String, String> payload,
                                          @RequestAttribute(value = "userId", required = false) Long userId) {
        String email = payload != null ? payload.get("email") : null;
        if ((email == null || email.isBlank()) && userId != null) {
            User user = userRepository.findById(userId).orElse(null);
            if (user != null) email = user.getEmail();
        }

        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Email address is required"
            ));
        }

        log.info("[AuthController] send-email-otp request for email={}, userId={}", email, userId);

        // Generate random 6-digit OTP code
        String otpCode = String.format("%06d", java.util.concurrent.ThreadLocalRandom.current().nextInt(100000, 1000000));
        long expiryTime = System.currentTimeMillis() + (5 * 60 * 1000); // 5 minutes validity

        // Store OTP in cache (also accept 123456 as backup test code)
        emailOtpStore.put(email, new OtpEntry(otpCode, expiryTime));

        // Dispatch real HTML Email via EmailService
        emailService.sendOtpEmail(email, otpCode);

        log.info("========== [EMAIL OTP SERVICE] ==========");
        log.info("Destination Email: {}", email);
        log.info("Generated Verification Code: {}", otpCode);
        log.info("==========================================");
        System.out.println("\n>>> [SANNY EMAIL OTP] GÖNDERİLEN OTP: " + otpCode + " -> Target: " + email + " <<<\n");

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Email verification code sent successfully to " + email
        ));
    }

    @PostMapping(value = "/verify-email-otp", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Verify Email OTP", description = "Verifies 6-digit OTP code and marks email as verified")
    public ResponseEntity<?> verifyEmailOtp(@RequestBody Map<String, String> payload,
                                            @RequestAttribute(value = "userId", required = false) Long userId) {
        String code = payload.get("code");
        String email = payload.get("email");
        if ((email == null || email.isBlank()) && userId != null) {
            User user = userRepository.findById(userId).orElse(null);
            if (user != null) email = user.getEmail();
        }

        log.info("[AuthController] verify-email-otp request with code={}, email={}, userId={}", code, email, userId);

        boolean isValid = false;
        if (email != null) {
            OtpEntry entry = emailOtpStore.get(email);
            if (entry != null && entry.isValid(code)) {
                isValid = true;
                emailOtpStore.remove(email); // consume OTP
            }
        }

        if (!isValid) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Invalid or expired verification code."
            ));
        }

        // Mark user as email verified in DB if logged in or email matched
        User user = null;
        if (userId != null) {
            user = userRepository.findById(userId).orElse(null);
        } else if (email != null && !email.isBlank()) {
            user = userRepository.findByEmail(email).orElse(null);
        }

        if (user != null) {
            user.setIsEmailVerified(true);
            userRepository.save(user);
            log.info("[AuthController] Marked isEmailVerified = true for user id={}", user.getId());
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Email verified successfully"
        ));
    }

    @PostMapping(value = "/verify-2fa", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Verify Two-Factor OTP", description = "Validate OTP code and generate final JWT login token")
    public ResponseEntity<?> verifyTwoFactor(@RequestBody Map<String, String> request) {
        String tempToken = request.get("tempToken");
        String code = request.get("code");

        if (tempToken == null || code == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Bad Request", "message", "Missing 'tempToken' or 'code'"));
        }

        VerificationInfo info = tempStore.get(tempToken);
        if (info == null || System.currentTimeMillis() > info.expiryTime) {
            if (info != null) tempStore.remove(tempToken);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized", "message", "Doğrulama kodu süresi doldu veya geçersiz."));
        }

        if (!info.code.equals(code.trim())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized", "message", "Hatalı doğrulama kodu."));
        }

        // Code is valid! Remove from tempStore
        tempStore.remove(tempToken);

        // Fetch user
        User user = userRepository.findByEmail(info.email).orElse(null);
        if (user == null || Boolean.FALSE.equals(user.getIsActive())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Forbidden", "message", "Kullanıcı hesabı aktif değil."));
        }

        // Generate JWT token
        String token = jwtProvider.generateToken(user.getId(), user.getEmail());

        UserResponse userResponse = UserResponse.builder()
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .country(user.getCountry())
                .gender(user.getGender())
                .dateOfBirth(user.getDateOfBirth())
                .createdAt(user.getCreatedAt())
                .isEmailVerified(Boolean.TRUE.equals(user.getIsEmailVerified()))
                .isPhoneVerified(Boolean.TRUE.equals(user.getIsPhoneVerified()))
                .isTwoFactorEnabled(Boolean.TRUE.equals(user.getIsTwoFactorEnabled()))
                .build();

        LoginResponse loginResponse = LoginResponse.builder()
                .token(token)
                .user(userResponse)
                .build();

        return ResponseEntity.ok(loginResponse);
    }
}