package com.jurisflow.shared.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BusinessException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public BusinessException(String message) {
        super(message);
        this.status = HttpStatus.BAD_REQUEST;
        this.code = "BUSINESS_ERROR";
    }

    public BusinessException(String message, HttpStatus status) {
        super(message);
        this.status = status;
        this.code = "BUSINESS_ERROR";
    }

    public BusinessException(String message, HttpStatus status, String code) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static BusinessException notFound(String resource) {
        return new BusinessException(resource + " nao encontrado(a)", HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    public static BusinessException forbidden() {
        return new BusinessException("Acesso negado", HttpStatus.FORBIDDEN, "FORBIDDEN");
    }

    public static BusinessException conflict(String message) {
        return new BusinessException(message, HttpStatus.CONFLICT, "CONFLICT");
    }
}
