package com.example.demo.exception;

// Cambio de contraseña rechazado. field indica el campo del formulario que tiene el problema.
public class PasswordChangeRejectedException extends RuntimeException {

    private final String field;

    public PasswordChangeRejectedException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
