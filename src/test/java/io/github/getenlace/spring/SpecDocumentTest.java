package io.github.getenlace.spring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpecDocumentTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void ensureServersUrl_injectsServer_whenMissingEntirely() throws Exception {
        String json = """
                { "openapi": "3.0.1", "info": { "title": "Test" }, "paths": {} }
                """;

        String result = SpecDocument.ensureServersUrl(json, "http://localhost:5001", objectMapper);

        JsonNode servers = objectMapper.readTree(result).get("servers");
        assertThat(servers.get(0).get("url").asText()).isEqualTo("http://localhost:5001");
    }

    @Test
    void ensureServersUrl_injectsServer_whenServersIsEmptyArray() throws Exception {
        String json = """
                { "openapi": "3.0.1", "info": {}, "paths": {}, "servers": [] }
                """;

        String result = SpecDocument.ensureServersUrl(json, "http://localhost:5001", objectMapper);

        JsonNode servers = objectMapper.readTree(result).get("servers");
        assertThat(servers).hasSize(1);
        assertThat(servers.get(0).get("url").asText()).isEqualTo("http://localhost:5001");
    }

    @Test
    void ensureServersUrl_injectsServer_whenExistingEntryHasNoUrl() throws Exception {
        String json = """
                { "openapi": "3.0.1", "info": {}, "paths": {}, "servers": [{ "description": "no url here" }] }
                """;

        String result = SpecDocument.ensureServersUrl(json, "http://localhost:5001", objectMapper);

        JsonNode servers = objectMapper.readTree(result).get("servers");
        assertThat(servers.get(0).get("url").asText()).isEqualTo("http://localhost:5001");
    }

    @Test
    void ensureServersUrl_leavesExistingServer_untouched() throws Exception {
        String json = """
                { "openapi": "3.0.1", "info": {}, "paths": {}, "servers": [{ "url": "https://api.example.com" }] }
                """;

        String result = SpecDocument.ensureServersUrl(json, "http://localhost:5001", objectMapper);

        JsonNode servers = objectMapper.readTree(result).get("servers");
        assertThat(servers).hasSize(1);
        assertThat(servers.get(0).get("url").asText()).isEqualTo("https://api.example.com");
    }
}
