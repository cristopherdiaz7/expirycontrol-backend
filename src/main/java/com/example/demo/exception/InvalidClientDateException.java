package com.example.demo.exception;

public class InvalidClientDateException extends RuntimeException {

    public InvalidClientDateException(String message) {
        super(message);
    }
}
