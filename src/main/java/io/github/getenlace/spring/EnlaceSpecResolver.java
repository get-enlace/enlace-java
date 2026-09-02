package io.github.getenlace.spring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;

/**
 * Resolves the OpenAPI document by probing known paths at startup, then
 * re-fetches from the resolved URL on every {@code /api/spec} request
 * (never cached — matches the "read fresh each load" adapter contract from
 * ARCHITECTURE.md §4, same as every other Enlace adapter).
 *
 * <p>Uses plain HTTP against localhost — no compile-time dependency on
 * springdoc, Swagger, or any specific OpenAPI generator. Works with any
 * library that serves an OpenAPI document at a conventional path.</p>
 */
class EnlaceSpecResolver {

    private static final Logger log = LoggerFactory.getLogger(EnlaceSpecResolver.class);

    /** Conventional OpenAPI paths probed at startup, in order. First one that returns valid JSON wins. */
    static final List<String> DEFAULT_PATHS = List.of(
            "/v3/api-docs",     // springdoc default
            "/v3/api-docs.yaml",// springdoc YAML variant
            "/swagger.json",    // common alternative
            "/openapi.json"     // generic
    );

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /** The URL that was successfully probed at startup. {@code null} until {@link #resolve} succeeds. */
    private volatile String resolvedUrl;

    EnlaceSpecResolver(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Probes the running app for an OpenAPI document. Called once, after the
     * server has started listening.
     *
     * @param baseUrl  the app's own listening address, e.g. {@code http://localhost:8080}
     * @param specUrl  explicit URL override from {@link EnlaceProperties#getSpecUrl()}, or {@code null}
     * @throws EnlaceConfigurationException if no valid OpenAPI document can be found
     */
    void resolve(String baseUrl, String specUrl) {
        if (specUrl != null && !specUrl.isBlank()) {
            String url = specUrl.startsWith("http") ? specUrl : baseUrl + specUrl;
            if (tryFetch(url) != null) {
                this.resolvedUrl = url;
                log.info("Enlace resolved the OpenAPI spec from {}", url);
                return;
            }
            throw new EnlaceConfigurationException(
                    "Enlace couldn't load an OpenAPI document from the configured specUrl: " + url);
        }

        for (String path : DEFAULT_PATHS) {
            String url = baseUrl + path;
            if (tryFetch(url) != null) {
                this.resolvedUrl = url;
                log.info("Enlace resolved the OpenAPI spec from {}", url);
                return;
            }
        }

        throw new EnlaceConfigurationException(
                "Enlace couldn't find an OpenAPI document. Tried: "
                + String.join(", ", DEFAULT_PATHS)
                + ". Set enlace.spec-url in application.properties to point at yours.");
    }

    /**
     * Fetches the spec fresh from the resolved URL and applies light post-processing (see
     * {@link SpecDocument#ensureServersUrl}). Returns {@code null} if the URL hasn't been
     * resolved yet or if the re-fetch fails — the caller (never stored here, matching the
     * "read fresh each load" adapter contract from ARCHITECTURE.md §4) decides how to report
     * that.
     */
    String fetchSpec() {
        if (resolvedUrl == null) {
            return null;
        }
        String json = tryFetch(resolvedUrl);
        if (json == null) {
            return null;
        }
        return SpecDocument.ensureServersUrl(json, baseAuthority(resolvedUrl), objectMapper);
    }

    String getResolvedUrl() {
        return resolvedUrl;
    }

    private String tryFetch(String url) {
        try {
            String body = restTemplate.getForObject(url, String.class);
            if (body != null && isValidOpenApi(body)) {
                return body;
            }
        } catch (Exception ignored) {
            // Network error, non-JSON body, etc. — this candidate didn't work.
        }
        return null;
    }

    /** {@code scheme://host:port} of the URL the spec was actually fetched from — used as the fallback {@code servers[0].url}. */
    private static String baseAuthority(String url) {
        try {
            URI uri = URI.create(url);
            return uri.getScheme() + "://" + uri.getAuthority();
        } catch (Exception e) {
            return url;
        }
    }

    private boolean isValidOpenApi(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            return root.isObject() && (root.has("openapi") || root.has("swagger"));
        } catch (Exception e) {
            return false;
        }
    }
}
