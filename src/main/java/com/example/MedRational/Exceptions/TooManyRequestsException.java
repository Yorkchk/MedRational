package com.example.MedRational.Exceptions;

import lombok.Getter;

// Answered as 429 with a Retry-After header (GlobalExceptionHandler)
@Getter
public class TooManyRequestsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(long retryAfterSeconds) {
        super("Too many attempts. Please try again later.");
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
