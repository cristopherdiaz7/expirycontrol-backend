package com.example.demo.exception;

public class PasswordTooLongException extends RuntimeException {

    public PasswordTooLongException(String message) {
        super(message);
    }
}
