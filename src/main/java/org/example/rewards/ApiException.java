package org.example.rewards;

import org.springframework.http.HttpStatus;

final class ApiException extends RuntimeException {
    final HttpStatus status;
    final String code;

    ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    static ApiException missing(String resource) {
        return new ApiException(HttpStatus.NOT_FOUND, resource + "_NOT_FOUND", "Resource does not exist.");
    }

    static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
