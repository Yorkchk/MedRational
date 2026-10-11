package com.example.MedRational.Exceptions;

// Closed suggestions are archived and read-only; mapped to 409 Conflict
public class SuggestionClosedException extends RuntimeException {
    public SuggestionClosedException(String msg) { super(msg); }
}
