package com.example.MedRational.Exceptions;

// Right password, but the sign-up code was never entered; mapped to 403 Forbidden
public class EmailNotVerifiedException extends RuntimeException {
    public EmailNotVerifiedException(String msg) { super(msg); }
}
