package io.github.getenlace.spring;

/**
 * Thrown when the Enlace adapter can't be configured correctly — most commonly, when no
 * OpenAPI document could be resolved at startup. Never crashes the host: the adapter logs
 * this loudly and lets the app keep running, so {@code GET /{path}/api/spec} reports the
 * failure (503) instead of silently rendering an empty canvas.
 */
public class EnlaceConfigurationException extends RuntimeException {

    public EnlaceConfigurationException(String message) {
        super(message);
    }

    public EnlaceConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
