package com.santsg.tourvisio.exception;

/**
 * TourVisio authentication (login) hataları için exception.
 *
 * <p>Token alınamadığında ya da login başarısız olduğunda fırlatılır.
 * {@link GlobalExceptionHandler} tarafından yakalanarak 503 döner.</p>
 */
public class TourVisioAuthException extends RuntimeException {

    public TourVisioAuthException(String message) {
        super(message);
    }

    public TourVisioAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
