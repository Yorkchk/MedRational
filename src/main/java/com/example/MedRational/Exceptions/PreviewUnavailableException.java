package com.example.MedRational.Exceptions;

public class PreviewUnavailableException extends RuntimeException {
    public PreviewUnavailableException(String msg) { super(msg); }
    public PreviewUnavailableException(String msg, Throwable cause) { super(msg, cause); }
}
