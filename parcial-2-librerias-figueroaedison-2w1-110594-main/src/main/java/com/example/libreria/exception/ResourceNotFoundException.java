package com.example.libreria.exception;

/**
 * Excepción lanzada cuando no se encuentra un recurso solicitado
 * (Usuario, Libro, Reserva, etc.)
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
