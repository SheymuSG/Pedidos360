package com.pedidos360.rabbitadmin.dto;

/**
 * Reglas de nombres compartidas por todos los DTO. Se rechaza el prefijo "amq."
 * porque RabbitMQ lo reserva para sus propios objetos.
 */
public final class ValidationPatterns {

    public static final String BROKER_NAME = "^(?!amq\\.)[A-Za-z0-9][A-Za-z0-9._:-]{0,254}$";
    public static final String BROKER_NAME_MESSAGE =
            "debe tener 1-255 caracteres alfanumericos, punto, guion, guion bajo o dos puntos, "
                    + "empezar con letra o numero y no usar el prefijo reservado 'amq.'";

    /** Routing key / binding key: admite comodines de topic (* y #) y puede ser vacia (fanout). */
    public static final String ROUTING_KEY = "^[A-Za-z0-9._*#:-]{0,255}$";
    public static final String ROUTING_KEY_MESSAGE =
            "solo admite letras, numeros, '.', '-', '_', ':', '*' y '#' (maximo 255 caracteres)";

    private ValidationPatterns() {}
}
