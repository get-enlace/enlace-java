package io.github.getenlace.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Auto-configures the Enlace adapter: mounts the embedded {@code @get-enlace/ui} bundle under
 * {@link EnlaceProperties#getPath()} and resolves the app's own OpenAPI document once the
 * embedded servlet container has actually started listening.
 *
 * <p>Zero-config for a project already running springdoc conventionally — every property on
 * {@link EnlaceProperties} has a sensible default, so just adding this starter as a
 * dependency is enough.</p>
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(WebMvcConfigurer.class)
@EnableConfigurationProperties(EnlaceProperties.class)
public class EnlaceAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(EnlaceAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    RestTemplate enlaceRestTemplate() {
        return new RestTemplate();
    }

    // Deliberately no @ConditionalOnMissingBean ObjectMapper fallback here. A fallback bean
    // races Spring Boot's own JacksonAutoConfiguration for the ObjectMapper singleton slot —
    // depending on auto-configuration ordering, a plain `new ObjectMapper()` from here can win
    // and get wired into Spring MVC's message converters, silently stripping JavaTimeModule
    // and every other Boot Jackson customization from the *entire host app's* JSON responses,
    // not just this adapter's. Any servlet app with our required WebMvcConfigurer on the
    // classpath already pulls in spring-boot-starter-json (hence JacksonAutoConfiguration's
    // own properly-configured ObjectMapper bean), so autowiring the app's existing bean
    // directly is both simpler and safe — a missing ObjectMapper bean would be a genuinely
    // broken host app, worth failing fast on rather than papering over.
    @Bean
    EnlaceSpecResolver enlaceSpecResolver(RestTemplate enlaceRestTemplate, ObjectMapper objectMapper) {
        return new EnlaceSpecResolver(enlaceRestTemplate, objectMapper);
    }

    /**
     * Registered as a plain servlet filter, not a {@code @RestController} — see
     * {@link EnlaceFilter}'s Javadoc for why. {@code order} deliberately unset (defaults to
     * lowest precedence): nothing here depends on running ahead of security or encoding
     * filters, it just needs to run before {@code DispatcherServlet} dispatch, which any
     * servlet filter does.
     */
    @Bean
    FilterRegistrationBean<EnlaceFilter> enlaceFilter(
            EnlaceSpecResolver enlaceSpecResolver, EnlaceProperties properties) {
        String mount = trim(properties.getPath());
        FilterRegistrationBean<EnlaceFilter> registration =
                new FilterRegistrationBean<>(new EnlaceFilter(enlaceSpecResolver, mount));
        registration.addUrlPatterns("/" + mount, "/" + mount + "/*");
        registration.setName("enlaceFilter");
        return registration;
    }

    @Bean
    WebMvcConfigurer enlaceWebMvcConfigurer(EnlaceProperties properties) {
        String mount = "/" + trim(properties.getPath()) + "/**";
        return new WebMvcConfigurer() {
            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {
                registry.addResourceHandler(mount)
                        .addResourceLocations(EnlaceDefaults.UI_RESOURCE_LOCATION);
            }
        };
    }

    /**
     * Resolves the OpenAPI spec once the embedded servlet container reports the port it's
     * actually listening on (handles {@code server.port=0} / random-port test setups, unlike
     * reading {@code server.port} from config directly). Runs on a background thread so a
     * slow or failing probe doesn't hold up application startup — {@link EnlaceFilter}
     * reports the failure (503) on the first {@code /api/spec} request instead.
     */
    @Bean
    ApplicationListener<WebServerInitializedEvent> enlaceStartupResolver(
            EnlaceSpecResolver enlaceSpecResolver, EnlaceProperties properties, ServerProperties serverProperties) {
        return event -> {
            int port = event.getWebServer().getPort();
            String contextPath = serverProperties.getServlet().getContextPath();
            String baseUrl = "http://localhost:" + port + (contextPath == null ? "" : contextPath);

            Thread resolverThread = new Thread(() -> {
                try {
                    enlaceSpecResolver.resolve(baseUrl, properties.getSpecUrl());
                    log.info("Enlace UI available at {}/{}/", baseUrl, trim(properties.getPath()));
                } catch (Exception e) {
                    log.error("Enlace failed to resolve an OpenAPI document at startup.", e);
                }
            }, "enlace-spec-resolver");
            resolverThread.setDaemon(true);
            resolverThread.start();
        };
    }

    private static String trim(String path) {
        String trimmed = path;
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
