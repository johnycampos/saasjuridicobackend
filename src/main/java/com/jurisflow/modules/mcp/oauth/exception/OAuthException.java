package com.jurisflow.modules.mcp.oauth.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class OAuthException extends RuntimeException {
    private final String error;
    private final String errorDescription;
    private final HttpStatus status;

    public OAuthException(String error, String errorDescription) {
        this(error, errorDescription, HttpStatus.BAD_REQUEST);
    }

    public OAuthException(String error, String errorDescription, HttpStatus status) {
        super(errorDescription);
        this.error = error;
        this.errorDescription = errorDescription;
        this.status = status;
    }
}
