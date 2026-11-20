package org.example.rewards;

import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

import static org.example.rewards.Models.ApiError;

@RestControllerAdvice
class ApiErrors extends ResponseEntityExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> domainError(ApiException ex) {
        ApiRequestLog.failure(ex.code, ex.getMessage());
        return ResponseEntity.status(ex.status).body(new ApiError(ex.code, ex.getMessage()));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                              HttpStatusCode status, WebRequest request) {
        var reason = HttpStatus.valueOf(status.value());
        String code = status.value() == 400 ? "INVALID_REQUEST" : reason.name();
        String message = safeReason(ex, reason);
        ApiRequestLog.failure(code, message);
        return super.handleExceptionInternal(ex, new ApiError(code, message), headers, status, request);
    }

    private static String safeReason(Exception ex, HttpStatus fallback) {
        if (ex instanceof MissingRequestHeaderException header) {
            return "Required header " + header.getHeaderName() + " is missing.";
        }
        if (ex instanceof MethodArgumentTypeMismatchException mismatch) {
            var type = mismatch.getRequiredType();
            return "Invalid " + mismatch.getName() + (type == null ? "." : " (expected " + type.getSimpleName() + ").");
        }
        if (ex instanceof MethodArgumentNotValidException invalid) {
            if (invalid.getBindingResult().getTarget() instanceof Models.IssueReward input) {
                ApiRequestLog.field("userId", input.userId());
                ApiRequestLog.field("rewardId", input.rewardId());
            }
            var error = invalid.getBindingResult().getFieldError();
            if (error != null) return error.getField() + " " + error.getDefaultMessage();
        }
        if (ex instanceof HandlerMethodValidationException invalid && !invalid.getParameterValidationResults().isEmpty()) {
            var param = invalid.getParameterValidationResults().getFirst();
            return "Invalid parameter " + param.getMethodParameter().getParameterName() + ".";
        }
        if (ex instanceof HttpMessageNotReadableException) {
            if (ex.getCause() instanceof UnrecognizedPropertyException) return "Unknown JSON field.";
            if (ex.getCause() instanceof MismatchedInputException mismatch && !mismatch.getPath().isEmpty()) {
                String field = mismatch.getPath().getFirst().getPropertyName();
                if ("userId".equals(field) || "rewardId".equals(field)) return field + " must be a JSON integer.";
            }
            return "Invalid JSON body or field type.";
        }
        return fallback.getReasonPhrase();
    }
}
