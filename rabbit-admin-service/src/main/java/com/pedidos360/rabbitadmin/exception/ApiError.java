package com.pedidos360.rabbitadmin.exception;

import java.time.Instant;
import java.util.List;

/** Cuerpo uniforme de todas las respuestas de error. */
public record ApiError(Instant timestamp, int status, String error, String message, String path,
                       List<String> details) {
}
