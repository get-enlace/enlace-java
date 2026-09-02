package io.github.getenlace.spring;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Serves the two things not covered by the static resource handler registered in
 * {@link EnlaceAutoConfiguration}: a redirect from the mount path to its {@code index.html},
 * and the resolved OpenAPI spec.
 *
 * <p>Deliberately a plain servlet {@code Filter}, not a {@code @RestController} — a
 * controller is discoverable by Spring-MVC-mapping-based OpenAPI generators (springdoc et
 * al.), which would then list Enlace's own internal routes alongside the host app's real API
 * operations in its generated spec. A filter runs ahead of {@code DispatcherServlet} and
 * never registers a request mapping, so it stays invisible to that introspection — the same
 * reason {@code enlace-dotnet} mounts its equivalent routes as raw ASP.NET Core middleware
 * ({@code app.Map(...)}) rather than a controller action.</p>
 */
class EnlaceFilter extends HttpFilter {

    private final EnlaceSpecResolver specResolver;

    /** Mount path, normalized: no leading or trailing slash (see {@link EnlaceProperties#getPath()}). */
    private final String mountPath;

    EnlaceFilter(EnlaceSpecResolver specResolver, String mountPath) {
        this.specResolver = specResolver;
        this.mountPath = mountPath;
    }

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String path = request.getRequestURI();
        String prefix = "/" + mountPath;

        if (path.equals(prefix)) {
            // No trailing slash — add one so the relative redirect below resolves correctly.
            response.sendRedirect(prefix + "/");
            return;
        }

        if (path.equals(prefix + "/")) {
            // The UI's own index.html, served by the static resource handler.
            response.sendRedirect("index.html");
            return;
        }

        if (path.equals(prefix + EnlaceDefaults.SPEC_ENDPOINT_PATH)) {
            serveSpec(response);
            return;
        }

        chain.doFilter(request, response);
    }

    private void serveSpec(HttpServletResponse response) throws IOException {
        if (specResolver.getResolvedUrl() == null) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.getWriter().write("Enlace: the OpenAPI document hasn't resolved. Check the application "
                    + "log for the startup error, or set enlace.spec-url explicitly.");
            return;
        }

        String json = specResolver.fetchSpec();
        if (json == null) {
            response.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
            response.getWriter().write("Enlace: couldn't re-fetch the OpenAPI document from "
                    + specResolver.getResolvedUrl() + ".");
            return;
        }

        response.setContentType("application/json");
        response.getWriter().write(json);
    }
}
