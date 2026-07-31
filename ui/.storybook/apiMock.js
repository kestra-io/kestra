// Storybook/Vitest API double.
//
// WHY THIS EXISTS
// ---------------
// There is no backend behind Storybook, yet almost every component tree issues HTTP calls. Before
// this module the only stub was `axios.defaults.adapter` (see preview.jsx), which covers just the 4
// files in src/ that still import axios. Everything else is fetch-based:
//
//   * the generated SDK (packages/kestra-sdk/src/openapi/*) resolves
//     `options.fetch ?? _config.fetch ?? globalThis.fetch` per request (openapi/client/client.gen.ts),
//   * the axios-like facade (packages/kestra-sdk/src/client-facade.ts) calls bare global `fetch`.
//
// `setMockClient()` only swaps the FACADE's methods, so every generated-SDK call used to escape to
// the Vitest browser dev server. That is where the storybook warnings came from: a GET came back 200
// with a non-JSON body, hey-api's `parseAs: "auto"` handed the store the body as TEXT (hence
// `[Vue warn] Invalid prop "plugins". Expected Array, got String`), and a POST 404'd into a thrown
// error that surfaced as `[FederatedModule] Failed to load plugin UI manifest` plus anonymous
// `PromiseRejectionEvent`s, often after the story had already finished ("unknown test").
//
// So the interception has to happen at the fetch layer, which is what this module does. It is
// imported FIRST by both `preview.jsx` (dev Storybook + tests) and `vitest.setup.js` (tests only) and
// deliberately imports nothing from `src/` or the SDK: pulling either in would evaluate
// `override/utils/route.ts` and the SDK client before the patch is installed.
//
// Writing a story stays cheap: an API route with no handler still answers, with an empty array, so
// nothing throws. But it is never silent — the route, the story that hit it and the URL are logged
// once per story, so a blank-looking story is traceable to the missing mock in one line instead of
// surfacing as an HTML-string-shaped prop three components deep.

const API_MARKER = "/api/v1"

/**
 * Payloads for the API routes stories actually reach, keyed by `METHOD <path from /api/v1 onward>`.
 *
 * Paths are matched prefix-agnostically (see `apiPath`), so the tenant-scoped `/api/v1/main/flows`
 * (route.ts `basePath()`) and the tenant-less `/api/v1/plugins` (`basePathWithoutTenant()`) are keyed
 * the same way, and it makes no difference whether the caller built an absolute or a relative URL.
 *
 * A `:name` segment matches any single segment and a trailing `*` matches the rest of the path, so
 * path parameters are covered. Exact keys win over patterned ones. A value may be a plain payload or
 * a `(context) => payload` function.
 *
 * Empty-but-correctly-SHAPED payloads matter: an object where a store expects an array is what turns
 * a missing mock into a `TypeError: data.map is not a function` deep inside a component. Each shape
 * below is the endpoint's `200` type from packages/kestra-sdk/src/openapi/types.gen.ts.
 */
const HANDLERS = {
    // --- plugins ------------------------------------------------------------------------------
    "GET /plugins": {results: [], total: 0},
    "GET /plugins/groups/subgroups": [],
    "GET /plugins/icons": {},
    "GET /plugins/icons/groups": {},
    "GET /plugins/icons/:cls": {icon: null},
    "GET /plugins/inputs": [],
    "GET /plugins/triggers": {results: [], total: 0},
    "GET /plugins/schemas/:type": {},
    "POST /plugins/pluginUiManifest": {manifest: {}},

    // --- flows --------------------------------------------------------------------------------
    "GET /flows/:namespace": [],
    "GET /flows/:namespace/:id/revisions": [],
    "GET /flows/:namespace/:id/dependencies": {nodes: [], edges: []},

    // --- namespaces ---------------------------------------------------------------------------
    "POST /namespaces/autocomplete": [],
    "GET /namespaces/:namespace/files/directory": [],

    // --- executions ---------------------------------------------------------------------------
    "GET /executions/search": {results: [], total: 0},
    "GET /outputs/:executionId": [],

    // --- dashboards & misc --------------------------------------------------------------------
    "POST /dashboards/charts/preview": {results: [], total: 0},
    "GET /concurrency-limit/search": {results: [], total: 0},
}

/**
 * Patterned route keys (those containing a `:param` or trailing `*`), pre-split into segments.
 * Sorted with the most segments first so the most specific pattern wins.
 */
const PATTERNS = Object.keys(HANDLERS)
    .filter((key) => key.includes("/:") || key.endsWith("*"))
    .map((key) => ({key, segments: key.split("/")}))
    .sort((a, b) => b.segments.length - a.segments.length)

function matchesPattern(segments, requestSegments) {
    for (let i = 0; i < segments.length; i++) {
        const segment = segments[i]
        if (segment === "*") return true
        if (i >= requestSegments.length) return false
        if (segment.startsWith(":")) continue
        if (segment !== requestSegments[i]) return false
    }
    return segments.length === requestSegments.length
}

/**
 * The `/api/v1`-onward portion of a URL, or `undefined` when the URL is not an API call.
 * Matching on `URL.pathname` (never the raw string) keeps dev-server routes — `/@vite/`, `/@fs/`,
 * `/@id/`, `/__vitest_*`, `/monaco/*` — out of scope: none of them contain `/api/v1`.
 */
function apiPath(rawUrl) {
    let pathname
    try {
        // blob:/data: URLs (monaco workers) throw or have no meaningful pathname — pass those through.
        pathname = new URL(rawUrl, window.location.href).pathname
    } catch {
        return undefined
    }

    const index = pathname.indexOf(API_MARKER)
    if (index === -1) return undefined

    const path = pathname.slice(index + API_MARKER.length)
    // Drop the tenant segment so `/main/flows/x` and `/flows/x` resolve to the same handler key.
    return path.replace(/^\/main(?=\/|$)/, "")
}

const reported = new Set()
let currentStory = ""

/**
 * Report an API route no handler covers.
 *
 * The request is still answered (a non-2xx would restart the `while (true)` retry loop in the SDK's
 * server-sent-events client and re-create the very rejection cascade this module removes), so this
 * log line is the ONLY signal that a story is rendering against empty data — a story stays cheap to
 * write, but nobody has to guess why it renders blank. De-duplicated per route because a polling
 * store multiplied by 55 story files would flood CI.
 */
function reportUnmocked(key, rawUrl) {
    if (reported.has(key)) return
    reported.add(key)
    // console.warn rather than console.error: some CI gates fail a build on console.error.
    // The key is what a handler must be registered under; the raw URL is what was actually called.
    console.warn(
        `[storybook] unmocked API request: ${key} — returning empty data, so this story renders without it.`
        + ` Add a handler in .storybook/apiMock.js (or mock it in the story).`
        + `\n  story: ${currentStory || "unknown"}\n  url:   ${rawUrl}`,
    )
}

/**
 * Scope the reporting to one story: names it in the warnings and clears the de-duplication record,
 * so an unmocked route is attributed to every story it affects rather than only the first.
 */
export function beginStoryScope(label) {
    currentStory = label ?? ""
    reported.clear()
}

/**
 * Resolve an API call to `{status, data}`. This is the single source of truth shared by the fetch
 * wrapper below and by `mockClientFallback()`, which story-local `setMockClient()` catch-alls use.
 */
export function resolveApiRequest(method, rawUrl, context = {}) {
    const path = apiPath(rawUrl) ?? rawUrl
    const upperMethod = method.toUpperCase()
    const key = `${upperMethod} ${path}`

    if (key in HANDLERS) {
        const handler = HANDLERS[key]
        return {status: 200, data: typeof handler === "function" ? handler(context) : handler}
    }

    const requestSegments = key.split("/")
    const pattern = PATTERNS.find(({segments}) => matchesPattern(segments, requestSegments))
    if (pattern) {
        const handler = HANDLERS[pattern.key]
        return {status: 200, data: typeof handler === "function" ? handler(context) : handler}
    }

    reportUnmocked(key, rawUrl)
    // An empty ARRAY, not an empty object: it has `length`, `map`, `filter` and is iterable, so a
    // component that expected a list degrades to "no rows" instead of throwing
    // `TypeError: data.map is not a function` somewhere far from the missing mock. Paged consumers
    // reading `.results` get undefined, which they already guard for. Either way the warning above
    // is the signal — this default only keeps the story renderable.
    return {status: 200, data: []}
}

/**
 * Axios-like adapter over {@link resolveApiRequest}, for stories that install their own
 * `setMockClient()` and need a default for the URIs they don't handle themselves.
 */
export function mockClientFallback(method, uri, data) {
    const {status, data: payload} = resolveApiRequest(method, uri, {body: data})
    return {data: payload, status, statusText: "OK", headers: {"content-type": "application/json"}}
}

function jsonResponse({status, data}) {
    // Always a real JSON content-type with a non-empty body: with no content-type hey-api's
    // `getParseAs(null)` returns "stream" and hands a ReadableStream to the store, and a 204 or an
    // empty body is special-cased by both parsers into null/{}/response.body.
    return new Response(JSON.stringify(data ?? {}), {
        status,
        statusText: status < 400 ? "OK" : "Error",
        headers: {"content-type": "application/json"},
    })
}

/**
 * The installed wrapper, exported so `preview.jsx` can also hand it to `configureClient({fetch})`.
 * The generated SDK resolves `options.fetch ?? _config.fetch ?? globalThis.fetch`, so pinning it
 * there makes the generated-SDK path explicit instead of relying on the global patch alone.
 */
export let apiFetch

function installFetchMock() {
    if (window.fetch.__kestraApiMock) {
        apiFetch = window.fetch
        return
    }

    const native = window.fetch.bind(window)

    // A plain function that never touches `this`: the SDK calls its fetch reference UNBOUND
    // (`const _fetch = opts.fetch!; await _fetch(request)`), so a method-style wrapper would break.
    const wrapper = (input, init) => {
        const isRequest = typeof Request !== "undefined" && input instanceof Request
        const rawUrl = isRequest ? input.url : String(input)
        const path = apiPath(rawUrl)

        // Anything that is not an API call — Vite's module graph, monaco assets, the storybook and
        // vitest channels — goes to the untouched native fetch.
        if (path === undefined) return native(input, init)

        const signal = init?.signal ?? (isRequest ? input.signal : undefined)
        if (signal?.aborted) {
            return Promise.reject(new DOMException("Aborted", "AbortError"))
        }

        const method = init?.method ?? (isRequest ? input.method : "GET")
        // `.clone()` so the original body stays intact for anything downstream.
        const bodyPromise = isRequest && input.body ? input.clone().text() : Promise.resolve(init?.body)

        return bodyPromise
            .catch(() => undefined)
            .then((body) => jsonResponse(resolveApiRequest(method, rawUrl, {body})))
    }

    wrapper.__kestraApiMock = true
    window.fetch = wrapper
    apiFetch = wrapper
}

/**
 * Monaco rejects the pending work of its own `Delayer`s when an editor is disposed ("Canceled:
 * Canceled"), so tearing a story down races monaco's cleanup. It happens entirely inside
 * monaco-editor, is not actionable, and is the only rejection class deliberately dropped here.
 *
 * Both spellings are needed: `/monaco/(esm|min)/vs` is the copy served from public/ at runtime, while
 * `monaco-editor/esm/vs` is what the Vite dev server serves out of node_modules during tests. The
 * previous filter in preview.jsx only listed the former, which is why these still reached the log.
 */
const MONACO_STACK = /monaco-editor[/\\]esm[/\\]vs|[/\\]monaco[/\\](esm|min)[/\\]vs/

/**
 * Print what a rejection actually was. Vitest reports an unhandled rejection by logging the event
 * object, which for a `PromiseRejectionEvent` prints as `{isTrusted: true}` — no message, no stack,
 * no way to tell which call failed. This adds the reason (and, for DOM-Event reasons, the element
 * that failed to load), and deliberately does NOT call preventDefault() except for the monaco
 * teardown race above, so vitest still reports everything that could point at a real problem.
 */
function installRejectionReporter() {
    if (window.__kestraRejectionReporter) return
    window.__kestraRejectionReporter = true

    window.addEventListener("unhandledrejection", (event) => {
        const reason = event?.reason
        if (typeof reason?.stack === "string" && MONACO_STACK.test(reason.stack)) {
            event.preventDefault()
            return
        }

        if (typeof Event !== "undefined" && reason instanceof Event) {
            const target = reason.target
            console.error(`[storybook] unhandled rejection with a DOM ${reason.type} event`, target?.src ?? target?.currentSrc ?? target)
            return
        }

        console.error("[storybook] unhandled rejection:", reason?.stack ?? reason?.message ?? reason)
    })
}

installFetchMock()
installRejectionReporter()
