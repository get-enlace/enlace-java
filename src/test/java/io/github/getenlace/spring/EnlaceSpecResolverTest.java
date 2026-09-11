package io.github.getenlace.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class EnlaceSpecResolverTest {

    private static final String BASE_URL = "http://localhost:5000";
    private static final String OPEN_API_JSON =
            """
            { "openapi": "3.0.1", "info": { "title": "Test", "version": "1.0" } }
            """;

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private EnlaceSpecResolver resolver;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        resolver = new EnlaceSpecResolver(restTemplate, new ObjectMapper());
    }

    @Test
    void resolve_usesSpecUrl_whenConfigured() {
        server.expect(requestTo("https://custom/openapi.json"))
                .andRespond(withSuccess(OPEN_API_JSON, MediaType.APPLICATION_JSON));

        resolver.resolve(BASE_URL, "https://custom/openapi.json");

        assertThat(resolver.getResolvedUrl()).isEqualTo("https://custom/openapi.json");
    }

    @Test
    void resolve_throws_whenSpecUrlConfigured_butUnreachable() {
        server.expect(requestTo("https://custom/openapi.json"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> resolver.resolve(BASE_URL, "https://custom/openapi.json"))
                .isInstanceOf(EnlaceConfigurationException.class)
                .hasMessageContaining("https://custom/openapi.json");
    }

    @Test
    void resolve_fallsBackThroughDefaultPaths_inOrder() {
        server.expect(requestTo(BASE_URL + "/v3/api-docs"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo(BASE_URL + "/v3/api-docs.yaml"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo(BASE_URL + "/swagger.json"))
                .andRespond(withSuccess(OPEN_API_JSON, MediaType.APPLICATION_JSON));

        resolver.resolve(BASE_URL, null);

        assertThat(resolver.getResolvedUrl()).isEqualTo(BASE_URL + "/swagger.json");
    }

    @Test
    void resolve_throws_withNamedPaths_whenNothingResolves() {
        for (String path : EnlaceSpecResolver.DEFAULT_PATHS) {
            server.expect(requestTo(BASE_URL + path))
                    .andRespond(withStatus(HttpStatus.NOT_FOUND));
        }

        assertThatThrownBy(() -> resolver.resolve(BASE_URL, null))
                .isInstanceOf(EnlaceConfigurationException.class)
                .hasMessageContaining("enlace.spec-url")
                .satisfies(ex -> EnlaceSpecResolver.DEFAULT_PATHS.forEach(
                        path -> assertThat(ex.getMessage()).contains(path)));
    }

    @Test
    void fetchSpec_reFetchesFresh_unmodified() {
        // Both expectations declared upfront — MockRestServiceServer refuses to register more
        // once actual requests have started flowing through it.
        server.expect(requestTo(BASE_URL + "/v3/api-docs"))
                .andRespond(withSuccess(OPEN_API_JSON, MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/v3/api-docs"))
                .andRespond(withSuccess(OPEN_API_JSON, MediaType.APPLICATION_JSON));

        resolver.resolve(BASE_URL, null);
        String result = resolver.fetchSpec();

        // No `servers` injected — a missing/relative servers[0].url is @get-enlace/ui's own
        // job to resolve client-side now (see resolveBaseUrl), so this adapter just passes
        // the freshly re-fetched document straight through, same as it arrived.
        assertThat(result).isEqualToIgnoringWhitespace(OPEN_API_JSON);
    }

    @Test
    void fetchSpec_returnsNull_beforeResolved() {
        assertThat(resolver.fetchSpec()).isNull();
    }
}
