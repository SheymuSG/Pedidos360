package com.pedidos360.rabbitadmin.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.time.Instant;
import java.util.List;

/** Traduce excepciones a respuestas JSON con mensajes claros. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** @Valid sobre cuerpo JSON o parametros (MethodArgumentNotValidException extiende BindException). */
    @ExceptionHandler(BindException.class)
    ResponseEntity<ApiError> onBind(BindException ex, HttpServletRequest request) {
        List<String> details = ex.getBindingResult().getAllErrors().stream()
                .map(e -> (e instanceof FieldError fe ? fe.getField() : e.getObjectName()) + ": " + e.getDefaultMessage())
                .sorted()
                .toList();
        return error(HttpStatus.BAD_REQUEST, "La peticion no es valida", request, details);
    }

    /** @Pattern en @PathVariable. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiError> onMethodValidation(HandlerMethodValidationException ex, HttpServletRequest request) {
        List<String> details = ex.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream())
                .map(e -> e.getDefaultMessage() == null ? "valor invalido" : e.getDefaultMessage())
                .toList();
        return error(HttpStatus.BAD_REQUEST, "La peticion no es valida", request, details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> onConstraint(ConstraintViolationException ex, HttpServletRequest request) {
        List<String> details = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .sorted()
                .toList();
        return error(HttpStatus.BAD_REQUEST, "La peticion no es valida", request, details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> onUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST,
                "El cuerpo no es un JSON valido o contiene un valor no permitido (p. ej. un type desconocido)",
                request, List.of());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiError> onNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(ResourceConflictException.class)
    ResponseEntity<ApiError> onConflict(ResourceConflictException ex, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(AmqpConnectException.class)
    ResponseEntity<ApiError> onBrokerDown(AmqpConnectException ex, HttpServletRequest request) {
        log.error("RabbitMQ no disponible: {}", ex.getMessage());
        return error(HttpStatus.SERVICE_UNAVAILABLE, "No hay conexion con RabbitMQ", request, List.of());
    }

    @ExceptionHandler(AmqpException.class)
    ResponseEntity<ApiError> onAmqp(AmqpException ex, HttpServletRequest request) {
        String text = String.valueOf(ex.getMessage()) + String.valueOf(ex.getCause());
        if (text.contains("PRECONDITION_FAILED")) {
            return error(HttpStatus.CONFLICT,
                    "El recurso ya existe con argumentos distintos; eliminelo antes de recrearlo", request, List.of());
        }
        log.error("Error de RabbitMQ: {}", ex.getMessage());
        return error(HttpStatus.BAD_GATEWAY, "RabbitMQ rechazo la operacion", request, List.of());
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String message, HttpServletRequest request,
                                           List<String> details) {
        ApiError body = new ApiError(Instant.now(), status.value(), status.getReasonPhrase(),
                message, request.getRequestURI(), details);
        return ResponseEntity.status(status).body(body);
    }
}
