package io.github.getenlace.spring;

/**
 * Constants shared across the adapter. Not configuration — nothing here is meant to be
 * overridden by a consuming application.
 */
final class EnlaceDefaults {

    private EnlaceDefaults() {
    }

    /**
     * Relative path, under the mount path, where the UI fetches the resolved spec. Fixed by
     * {@code @get-enlace/ui}'s client (see api/client.ts's {@code fetch('api/spec')}) — every
     * adapter, in every language, serves the spec at exactly this path under its mount.
     */
    static final String SPEC_ENDPOINT_PATH = "/api/spec";

    /**
     * Classpath location the embedded {@code @get-enlace/ui} bundle is served from. Populated
     * by {@code scripts/dev-sync-ui.sh} (local) or {@code scripts/ci-fetch-ui.sh} (CI) —
     * never committed, never edited by hand.
     */
    static final String UI_RESOURCE_LOCATION = "classpath:/enlace-ui-embedded/";
}
