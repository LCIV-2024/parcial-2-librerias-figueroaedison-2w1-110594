package com.example.libreria.exception;

/**
 * Excepción lanzada cuando no hay suficiente stock disponible
 * de un libro para crear una reserva
 */
public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String message) {
        super(message);
    }

    public InsufficientStockException(String message, Throwable cause) {
        super(message, cause);
    }
}