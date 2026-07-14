package com.santsg.tourvisio.exception;

public class TourVisioApiException extends RuntimeException {
    
    public TourVisioApiException(String message) {
        super(message);
    }

    public TourVisioApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
