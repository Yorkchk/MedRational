package com.example.MedRational.Exceptions;

// Sign-up with the email of a verified account; mapped to 409 Conflict
public class EmailAlreadyRegisteredException extends RuntimeException {
    public EmailAlreadyRegisteredException(String msg) { super(msg); }
}
