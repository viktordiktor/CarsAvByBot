package com.avby.avby.avby;

public class AvbyException extends RuntimeException {

    public AvbyException(String message) {
        super(message);
    }

    public AvbyException(String message, Throwable cause) {
        super(message, cause);
    }
}
