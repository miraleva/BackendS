package com.santsg.tourvisio.controller;

import com.santsg.tourvisio.dto.auth.ProfileUpdateRequest;
import com.santsg.tourvisio.dto.auth.UserResponse;
import com.santsg.tourvisio.entity.User;
import com.santsg.tourvisio.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/profile")
@Tag(name = "User Profile", description = "Endpoints for retrieving and updating user profile information")
@Slf4j
public class ProfileController {

    private final UserRepository userRepository;

    public ProfileController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get Profile", description = "Retrieve logged-in user's profile details")
    public ResponseEntity<?> getProfile(@RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            log.warn("[ProfileController] Access denied: userId attribute not found in request");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", "Unauthorized",
                    "message", "User session is invalid or missing"
            ));
        }

        log.info("[ProfileController] Fetching profile for userId={}", userId);
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.warn("[ProfileController] Profile retrieval failed: user id={} not found", userId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", "Not Found",
                    "message", "User profile not found"
            ));
        }

        UserResponse userResponse = mapToUserResponse(user);
        return ResponseEntity.ok(userResponse);
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update Profile", description = "Update logged-in user's profile fields")
    public ResponseEntity<?> updateProfile(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @Valid @RequestBody ProfileUpdateRequest request) {
        
        if (userId == null) {
            log.warn("[ProfileController] Profile update denied: userId attribute not found in request");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", "Unauthorized",
                    "message", "User session is invalid or missing"
            ));
        }

        log.info("[ProfileController] Updating profile for userId={}", userId);
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.warn("[ProfileController] Profile update failed: user id={} not found", userId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", "Not Found",
                    "message", "User profile not found"
            ));
        }

        // Safely update profile fields if they are provided
        if (request.getFirstName() != null && !request.getFirstName().isBlank()) {
            user.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null && !request.getLastName().isBlank()) {
            user.setLastName(request.getLastName());
        }
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            user.setPhone(request.getPhone());
        }
        
        // These fields are completely nullable, so they can be explicitly updated to null or empty
        user.setCountry(request.getCountry());
        user.setGender(request.getGender());
        user.setDateOfBirth(request.getDateOfBirth());

        User updatedUser = userRepository.save(user);
        log.info("[ProfileController] Profile updated successfully for userId={}", userId);

        UserResponse userResponse = mapToUserResponse(updatedUser);
        return ResponseEntity.ok(userResponse);
    }

    private UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .country(user.getCountry())
                .gender(user.getGender())
                .dateOfBirth(user.getDateOfBirth())
                .build();
    }
}
