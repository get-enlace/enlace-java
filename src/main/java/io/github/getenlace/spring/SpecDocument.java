package io.github.getenlace.spring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Light post-processing applied to the OpenAPI document before it's handed to the UI.
 */
final class SpecDocument {

    private SpecDocument() {
    }

    /**
     * The UI reads its request target from the spec's own {@code servers[0].url} — it does no
     * discovery of its own (see {@code @get-enlace/ui}'s store/workflowStore.ts). Not every
     * OpenAPI generator emits a {@code servers} array (springdoc usually does, but a
     * hand-written or differently-configured document might not), which would silently break
     * the zero-config promise for those cases — so when the fetched document has no usable
     * one, add one pointing at wherever we actually fetched the document from. Leaves an
     * existing {@code servers} entry untouched.
     */
    static String ensureServersUrl(String json, String fallbackBaseUrl, ObjectMapper objectMapper) {
        ObjectNode root;
        try {
            JsonNode parsed = objectMapper.readTree(json);
            if (!(parsed instanceof ObjectNode)) {
                return json;
            }
            root = (ObjectNode) parsed;
        } catch (Exception e) {
            return json;
        }

        if (hasUsableServer(root)) {
            return json;
        }

        ArrayNode servers = root.putArray("servers");
        servers.addObject().put("url", fallbackBaseUrl);

        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            return json;
        }
    }

    private static boolean hasUsableServer(ObjectNode root) {
        JsonNode servers = root.get("servers");
        if (servers == null || !servers.isArray()) {
            return false;
        }

        for (JsonNode server : servers) {
            JsonNode url = server.get("url");
            if (url != null && url.isTextual() && !url.asText().isBlank()) {
                return true;
            }
        }
        return false;
    }
}
