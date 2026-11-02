package com.talkcare.api;

/** A failed API call: either the server answered with an error, or it could not be reached. */
public class ApiException extends RuntimeException {
    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public ApiException(String message, Throwable cause) {
        super(message, cause);
        this.status = 0;
    }

    /** HTTP status, or 0 when the server was not reached at all. */
    public int status() {
        return status;
    }

    public boolean isUnreachable() {
        return status == 0;
    }
}
