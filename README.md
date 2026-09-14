# enlace-java

Spring Boot starter adapter for [Enlace](https://github.com/get-enlace/enlace) — an interactive visual execution graph for any OpenAPI 3.x API. Drag operations from your API onto a canvas, wire inputs to outputs, and run multi-step workflows concurrently from the browser. Full documentation: [get-enlace.github.io](https://get-enlace.github.io/).

[![Maven Central](https://img.shields.io/maven-central/v/io.github.get-enlace/enlace-spring-boot-starter.svg)](https://central.sonatype.com/artifact/io.github.get-enlace/enlace-spring-boot-starter)
[![Live Demo](https://img.shields.io/badge/demo-live%20on%20render-success)](https://enlace-fastapi.onrender.com/enlace/)
[![Star on GitHub](https://img.shields.io/github/stars/get-enlace/enlace?style=social)](https://github.com/get-enlace/enlace)

This adapter's job is intentionally small: it serves your OpenAPI document and the canvas UI's static bundle. Nothing else — wiring up a chain, concurrent execution, and holding credentials all happen client-side in the browser.

## Install

```xml
<dependency>
    <groupId>io.github.get-enlace</groupId>
    <artifactId>enlace-spring-boot-starter</artifactId>
    <version>0.0.9</version>
</dependency>
```

## Usage

Nothing to wire up by hand — adding the dependency is enough. Spring Boot's autoconfiguration
mounts the canvas at `/enlace` as soon as it's on the classpath, for a project already running
springdoc conventionally:

```java
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

Open `/enlace` and the canvas loads, reading your resolved spec from `/enlace/api/spec`.

To customize the mount path or point at an explicit spec:

```properties
# application.properties
enlace.path=enlace
enlace.spec-url=https://internal-host/custom/openapi.json
```

## Resolving your OpenAPI document

Unlike some adapters, you never pass your spec in directly — this one finds it for you:

1. **Zero-config default** — if your app already runs springdoc conventionally, its spec is
   already being served at `/v3/api-docs`; the adapter defaults to that path with no
   configuration needed.
2. **Auto-detect fallback** — if that doesn't resolve, it tries a short list of other
   conventional paths (`/v3/api-docs.yaml`, `/swagger.json`, `/openapi.json`) with a plain
   HTTP request to your app's own server — no reflection into route tables or framework
   internals.
3. **Explicit override** — set `enlace.spec-url` to point at anything else: a customized
   route, a different service's spec, a static file.
4. **Failure is loud, not fatal** — if nothing resolves, the app keeps running (the UI shell
   still loads) but `GET /enlace/api/spec` reports the failure with what was tried, and the
   application log names it at ERROR, rather than rendering a silent empty canvas.

## How it works

Adding the starter registers a servlet filter mounted at `enlace.path` (`/enlace` by default)
with two things on it:

- **`GET /{path}/api/spec`** — resolves your OpenAPI document once at startup (see above), then
  re-fetches it fresh on every request rather than caching it, so a spec that changes at
  runtime shows up on the next canvas reload with no restart needed.
- **The canvas UI's static assets**, at every other path under the mount — a prebuilt bundle
  shipped inside the `enlace-spring-boot-starter` jar itself, not fetched over the network at
  request time.

It's a plain filter rather than a `@RestController` on purpose — a controller is discoverable
by springdoc's own request-mapping scan, which would otherwise list Enlace's internal routes
alongside your API's real operations in your generated spec.

Everything past that — parsing the spec into operations, letting you wire a chain together,
actually sending requests to your API when you hit Run — happens in the browser, in the UI
bundle. This adapter never sees or proxies that traffic.

## Architecture

The Spring Boot starter is intentionally thin and symmetric across Enlace languages: it mounts a web filter that serves the static UI assets and exposes your OpenAPI document. All workflow execution runs client-side directly from your browser to your API endpoints, with local IndexedDB autosave.

## Contributing

See [`CONTRIBUTING.md`](CONTRIBUTING.md) for local development setup and build/test commands.

## License

[MIT](LICENSE)
