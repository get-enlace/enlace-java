# enlace-java

Spring Boot starter adapter for [Enlace](https://github.com/get-enlace/enlace-ui) — a visual,
chained-execution canvas for any OpenAPI-documented API. Drag operations from your API onto a
canvas, wire one call's output into the next call's input, and run the whole chain from the
browser. Docs and the full picture of what Enlace is: [get-enlace.github.io](https://get-enlace.github.io/).

This adapter's job is intentionally small: it serves your OpenAPI document and the canvas UI's
static bundle. Nothing else — wiring up a chain, running it, and holding credentials all happen
client-side, in the browser, once the page loads.

## Install

```xml
<dependency>
    <groupId>io.github.get-enlace</groupId>
    <artifactId>enlace-spring-boot-starter</artifactId>
    <version>0.0.2</version>
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

## Status

Pre-release scaffold. Persistence (saving/reloading workflows and credentials) is out of
scope for this phase — canvas state and credentials live in browser memory for the session
only. CI/CD is written (build/test on PR, dev/prod publishing — see
[`CONTRIBUTING.md`](CONTRIBUTING.md#cicd)) but dev/prod publishing can't run end to end until
its one-time external setup (GitHub Environments, Maven Central credentials) is done.

## Contributing

See [`CONTRIBUTING.md`](CONTRIBUTING.md) for local development setup and build/test commands.

## License

[MIT](LICENSE)
