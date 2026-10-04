package com.myproject.app.messenger.error;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** An expected failure, rendered as an RFC 9457 problem detail with a stable {@code code}. */
public class ApiException extends ErrorResponseException {

    private ApiException(HttpStatus status, String code, String detail, Map<String, Object> properties) {
        super(status, problem(status, code, detail, properties), null);
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail, Map<String, Object> properties) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        properties.forEach(problem::setProperty);
        return problem;
    }

    public static ApiException notFound(String what) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", what + " not found", Map.of());
    }

    public static ApiException notFound(String what, Map<String, Object> properties) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", what + " not found", properties);
    }

    public static ApiException conflict(String code, String detail) {
        return new ApiException(HttpStatus.CONFLICT, code, detail, Map.of());
    }

    public static ApiException forbidden(String detail) {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", detail, Map.of());
    }

    public static ApiException unprocessable(String code, String detail) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, detail, Map.of());
    }

    public static ApiException unauthorized(String detail) {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", detail, Map.of());
    }
}
