package com.santsg.tourvisio.exception;

import com.santsg.tourvisio.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

        private static final String GENERIC_ERROR_MESSAGE = "İşlem gerçekleştirilemedi.";

        @ExceptionHandler(ResourceNotFoundException.class)
        public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex) {
                log.warn("Resource not found: {}", ex.getMessage());
                ErrorResponse response = ErrorResponse.builder()
                                .timestamp(LocalDateTime.now())
                                .status(HttpStatus.NOT_FOUND.value())
                                .error(HttpStatus.NOT_FOUND.getReasonPhrase())
                                .message(ex.getMessage())
                                .build();
                return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
        }

        @ExceptionHandler(TourVisioAuthException.class)
        public ResponseEntity<ErrorResponse> handleTourVisioAuth(TourVisioAuthException ex) {
                // İç hata detayı yalnızca server log'a yazılır, API yanıtına sızdırılmaz
                log.error("TourVisio Auth Exception: {}", ex.getMessage(), ex);
                ErrorResponse response = ErrorResponse.builder()
                                .timestamp(LocalDateTime.now())
                                .status(HttpStatus.SERVICE_UNAVAILABLE.value())
                                .error(HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase())
                                .message(GENERIC_ERROR_MESSAGE)
                                .build();
                return new ResponseEntity<>(response, HttpStatus.SERVICE_UNAVAILABLE);
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
                log.warn("Validation failed for request: {}", ex.getMessage());
                List<String> details = ex.getBindingResult().getFieldErrors().stream()
                                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                                .collect(Collectors.toList());

                ErrorResponse response = ErrorResponse.builder()
                                .timestamp(LocalDateTime.now())
                                .status(HttpStatus.BAD_REQUEST.value())
                                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                                .message("Validation failed for input parameters")
                                .details(details)
                                .build();
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
        public ResponseEntity<ErrorResponse> handleResponseStatusException(
                        org.springframework.web.server.ResponseStatusException ex) {
                log.warn("Response status exception: {}", ex.getMessage());
                HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
                if (status == null)
                        status = HttpStatus.INTERNAL_SERVER_ERROR;
                ErrorResponse response = ErrorResponse.builder()
                                .timestamp(LocalDateTime.now())
                                .status(status.value())
                                .error(status.getReasonPhrase())
                                .message(ex.getReason() != null ? ex.getReason() : status.getReasonPhrase())
                                .build();
                return new ResponseEntity<>(response, status);
        }

        @ExceptionHandler(IllegalArgumentException.class)
        public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
                // İç hata detayı yalnızca log'a yazılır
                log.warn("Illegal argument exception: {}", ex.getMessage());
                ErrorResponse response = ErrorResponse.builder()
                                .timestamp(LocalDateTime.now())
                                .status(HttpStatus.BAD_REQUEST.value())
                                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                                .message(GENERIC_ERROR_MESSAGE)
                                .build();
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        @ExceptionHandler({ DataAccessException.class, SQLException.class })
        public ResponseEntity<ErrorResponse> handleDatabaseExceptions(Exception ex) {
                log.error("Database error occurred: {}", ex.getMessage(), ex);
                ErrorResponse response = ErrorResponse.builder()
                                .timestamp(LocalDateTime.now())
                                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                                .error(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase())
                                .message(GENERIC_ERROR_MESSAGE)
                                .build();
                return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex) {
                log.error("Unhandled exception occurred: {}", ex.getMessage(), ex);
                ErrorResponse response = ErrorResponse.builder()
                                .timestamp(LocalDateTime.now())
                                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                                .error(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase())
                                .message(GENERIC_ERROR_MESSAGE)
                                .build();
                return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
}
