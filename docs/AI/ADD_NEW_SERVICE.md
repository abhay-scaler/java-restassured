# Adding a New Service Within an Existing Application

Use this when an *existing* application (`appA` or `appB`) needs to talk to a **second backend** —
not a new independent application, just another set of endpoints under the same application's
identity, config file, and test suite. This is the `ServiceConfig` / `services.<name>.*`
mechanism. If the new API should really be its own application, go to
[`ADD_NEW_APPLICATION.md`](ADD_NEW_APPLICATION.md) instead — see [`ADD_NEW_API.md`](ADD_NEW_API.md)
for how to tell the two apart.

This mirrors `DESIGN.md`'s **How to add a new service within an application** and **Configuration
and environment strategy** sections — those are the source of truth for the underlying mechanism;
this is the actionable checklist.

## How the mechanism works (read before editing anything)

`ServiceConfig.of("<name>")` reads `services.<name>.<key>` from that application's active
properties file, falling back to the application's own top-level key when the service hasn't
overridden it. So registering a service with no properties at all still resolves to the
application's existing defaults — see `ServiceClientTests` for App A's example of exactly that.

`ServiceConfig` never learns "application" is a concept — it only asks `ConfigManager` for
whatever's active on the current thread, so it's automatically scoped to whichever application's
suite is running. This is why the mechanism composes cleanly with the app-selection layer instead
of needing its own `-D` flag.

## Checklist

1. **Add config keys** to the relevant application's environment properties file(s)
   (`src/test/resources/config/<app>/<env>.properties`) — only the keys that differ from the
   application's top-level defaults, usually `base.url` and `auth.*`. Use the dotted convention
   already used everywhere else in the file:
   ```properties
   services.orders.base.url=https://orders.internal.example.com
   services.orders.auth.type=BEARER_TOKEN
   services.orders.auth.token=${ORDERS_TOKEN}
   ```
   Not `base-url` or any other separator — `ConfigManager`/`ServiceConfig` only recognize the
   dotted form.
2. **Add endpoints** — a new `apps/<app>/endpoints/<Name>Endpoints.java` with the new service's
   paths, in that application's existing endpoints package.
3. **Add request/response POJOs** under that application's `apps/<app>/models/`.
4. **Write tests** extending `BaseTest`, using `new RestClient("<name>")` **instead of**
   `client()` — `client()` always builds the application's default spec; the named constructor is
   what routes to `services.<name>.*`.
5. **Register the test classes** in the relevant `<classes>` block(s) of that application's
   `suites/<app>/*.xml` files (the same suite files the application already uses — a new service
   does not get its own suite file).
6. **Add JSON Schemas** the new service needs under that application's `schemas/<app>/`, same
   convention as the application's existing schemas.

## What must NOT change

`builders/`, `clients/`, `config/` (the classes), `filters/`, `retry/`, and `reporting/` — none of
them need to change for a new service. If your plan touches one of them, you may have actually hit
a real gap in `ServiceConfig`/`AuthProvider`'s coverage (e.g. an auth type they don't support yet)
rather than something specific to your new service — raise it before implementing, per
[`../../AGENTS.md`](../../AGENTS.md) rule 1.

## Reference examples already in this repo

- `ServiceClientTests` (App A) — registers a `"users"` service with zero overrides, proving the
  fallback-to-defaults path.
- `AppAServiceConfigTests` / `AppBServiceConfigTests` — prove `ServiceConfig` resolution
  independently per application, including that `services.*` values never collide across apps.
- `ConfigManagerTests` — proves the underlying config-isolation guarantee
  (`ConfigManager.loadFor(app, env)`) that both service-config test classes build on.

## Verify before calling it done

- [ ] `services.<name>.*` keys use the dotted convention, not `-` or `_`.
- [ ] A test using `new RestClient("<name>")` resolves to the right base URL/auth (add a
      `ConfigManagerTests`-style assertion using `ConfigManager.resolveRawProperty` if you want a
      unit-level guarantee without a live HTTP call).
- [ ] No shared package outside `apps/<app>/` needed a change.
- [ ] Run [`REVIEW_CHECKLIST.md`](REVIEW_CHECKLIST.md) before opening the PR.
