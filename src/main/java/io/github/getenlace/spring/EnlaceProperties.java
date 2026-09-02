package io.github.getenlace.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Enlace adapter. Every property has a
 * sensible default — a project already running springdoc can add the starter
 * with zero configuration.
 */
@ConfigurationProperties(prefix = "enlace")
public class EnlaceProperties {

    /**
     * Mount path for the canvas UI. Defaults to {@code "enlace"}.
     * The UI is served at {@code /{path}} and the spec endpoint at
     * {@code /{path}/api/spec}. No leading or trailing slash (e.g. {@code "enlace"},
     * not {@code "/enlace"}).
     */
    private String path = "enlace";

    /**
     * Explicit URL or path of the OpenAPI document to load. When {@code null}
     * (the default), the adapter probes known paths ({@code /v3/api-docs},
     * {@code /swagger.json}, {@code /openapi.json}) against the running app
     * at startup and uses the first one that returns valid OpenAPI JSON.
     */
    private String specUrl;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getSpecUrl() {
        return specUrl;
    }

    public void setSpecUrl(String specUrl) {
        this.specUrl = specUrl;
    }
}
