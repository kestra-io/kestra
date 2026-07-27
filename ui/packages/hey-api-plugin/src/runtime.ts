// Runtime entry of @kestra-io/hey-api-plugin — the universal, everyone-needs-it half of a Kestra
// SDK. `createConfigureClient` wires a @hey-api/client-fetch `client` singleton for the Kestra API:
// the string/empty-body-aware body serializer, the QueryFilter-aware query serializer, the
// Content-Type/Accept request fixes, and Error-normalizing error interceptor. It is the fetch-based
// `configureClient` from client-sdk's hand-written src/index.ts, lifted out so the OSS UI SDK, the
// EE UI SDK, and client-sdk all share ONE copy.
//
// It is generic over the client and holds NO module state: it just clears + re-installs the
// interceptors and returns the same `client` instance. The "current instance" singleton and its
// accessors (useClient / setMockClient, plus the axios-like fetch facade that shares this client's
// interceptors) are app concerns and live in each app's SDK entry, not here.
//
// `formDataBodySerializer` is passed in rather than imported: @hey-api/client-fetch vendors a
// self-contained `./openapi/core/` into every generated SDK, so the multipart serializer is a
// per-SDK module instance. The request interceptor detects multipart endpoints by identity against
// that exact instance, so the caller supplies its own. This keeps the runtime free of any hard
// dependency (no axios, no @hey-api/*).

import {EnterpriseFeatureError, type EnterpriseFeatureConfig} from "./errors"

export {EnterpriseFeatureError} from "./errors"
export type {EnterpriseFeatureConfig, EnterpriseFeatureMatch} from "./errors"

/** Minimal structural shape of a @hey-api/client-fetch interceptor slot. */
interface FetchInterceptor {
    clear: () => void;
    use: (fn: (...args: any[]) => any) => void;
}

/** The subset of a @hey-api/client-fetch client that configureClient touches. */
export interface ConfigurableFetchClient {
    setConfig: (config: any) => unknown;
    interceptors: {
        request: FetchInterceptor;
        response: FetchInterceptor;
        error: FetchInterceptor;
    };
}

/** The per-request options bag the fetch client passes to request interceptors. */
interface ResolvedRequestOptionsLike {
    bodySerializer?: unknown;
    parseAs?: "json" | "text" | "blob" | "stream" | "arrayBuffer" | "formData" | (string & {});
    /** The templated OpenAPI path passed to the SDK call, e.g. "/api/v1/{tenant}/audit-logs/search" — NOT the resolved URL. */
    url?: string;
    [key: string]: unknown;
}

/** The generated SDK's own multipart body serializer (from its vendored core). */
interface FormDataBodySerializer {
    bodySerializer: (...args: any[]) => any;
}

function canBeJsonified(str: any): boolean {
    if (typeof str !== "string" && typeof str !== "object") return false
    try {
        const type = str.toString()
        return type === "[object Object]" || type === "[object Array]"
    } catch {
        return false
    }
}

function serializeQueryValue(val: unknown): string | undefined {
    if (canBeJsonified(val)) {
        return JSON.stringify(val)
    }
    return val?.toString()
}

/**
 * Build the shared `configureClient` for a @hey-api/client-fetch `client`.
 *
 * @param client the SDK's fetch client singleton (from its generated `client.gen`)
 * @param formDataBodySerializer the SDK's own multipart serializer (from its generated core), used
 *        to detect multipart endpoints by identity so we don't force `application/json` on them
 * @param enterpriseFeature optional — when set, a 404 whose route matches
 *        `enterpriseFeature.matchRoute` throws an {@link EnterpriseFeatureError} instead of the
 *        generic normalized error below. Only the SDK that spans both editions (client-sdk) needs
 *        this; the OSS/EE UI SDKs can omit it entirely and behavior is unchanged.
 * @returns a `configureClient(clientConfig?)` that installs the Kestra setup and returns `client`
 */
export function createConfigureClient<TClient extends ConfigurableFetchClient>(
    client: TClient,
    formDataBodySerializer: FormDataBodySerializer,
    enterpriseFeature?: EnterpriseFeatureConfig,
) {
    type ClientConfig = Parameters<TClient["setConfig"]>[0];

    return function configureClient(clientConfig: ClientConfig = {} as ClientConfig): TClient {
        client.interceptors.request.clear()
        client.interceptors.response.clear()
        client.interceptors.error.clear()

        client.setConfig({
            // The default jsonBodySerializer JSON-stringifies everything, including plain string
            // bodies (YAML, text/plain). Override to pass strings through as-is.
            bodySerializer: (body: unknown): unknown => {
                if (typeof body === "string") return body
                // buildClientParams initialises params.body as {} even for no-body operations.
                // Return '' so the client treats it as an absent body (no Content-Type, no body sent).
                if (body !== null && typeof body === "object" && !Array.isArray(body) && Object.keys(body as Record<string, unknown>).length === 0) return ""
                return JSON.stringify(body, (_key, value) => (typeof value === "bigint" ? value.toString() : value))
            },
            querySerializer(query: Record<string, any>) {
                const queryParameters = new URLSearchParams()
                for (const key in query) {
                    const param = query[key]
                    if (param === undefined) {
                        continue
                    }
                    const looksLikeQueryFilterArray =
                        Array.isArray(param) &&
                        param.length > 0 &&
                        typeof param[0] === "object" &&
                        param[0] != null &&
                        "field" in param[0] &&
                        "operation" in param[0] &&
                        "value" in param[0]

                    if (looksLikeQueryFilterArray) {
                        for (const qf of param) {
                            const keyField = String(qf.field)
                            const op = String(qf.operation)

                            if (
                                typeof qf.value === "object" &&
                                qf.value != null &&
                                !Array.isArray(qf.value)
                            ) {
                                for (const [k, v] of Object.entries(qf.value)) {
                                    const ser = serializeQueryValue(v)
                                    if (ser !== undefined) {
                                        queryParameters.append(`filters[${keyField}][${op}][${k}]`, ser)
                                    }
                                }
                            } else {
                                const ser = serializeQueryValue(qf.value)
                                if (ser !== undefined) {
                                    queryParameters.append(`filters[${keyField}][${op}]`, ser)
                                }
                            }
                        }
                    } else if (param instanceof Array) {
                        param.forEach((value: any) => {
                            const ser = serializeQueryValue(value)
                            if (ser !== undefined) {
                                queryParameters.append(key, ser)
                            }
                        })
                    } else {
                        const ser = serializeQueryValue(param)
                        if (ser !== undefined) {
                            queryParameters.append(key, ser)
                        }
                    }
                }
                return queryParameters.toString()
            },
            ...clientConfig,
        })

        // Restore Content-Type for POST/PUT/PATCH and set Accept for non-JSON responses.
        // The fetch client deletes Content-Type for body-less requests (opts.body === undefined),
        // but Kestra requires Content-Type: application/json on all POST/PUT/PATCH, even without a body.
        // Exception: endpoints that use formDataBodySerializer (e.g. createExecution, resumeExecution)
        // set 'Content-Type: null' to let the browser supply the multipart boundary automatically.
        // When no body is provided for those endpoints, we must not inject application/json —
        // Kestra will reject the request with 401 if Content-Type doesn't match multipart/form-data.
        client.interceptors.request.use((request: Request, opts: ResolvedRequestOptionsLike): Request => {
            const headers = new Headers(request.headers)
            let modified = false

            const method = request.method.toLowerCase()
            const isFormDataEndpoint = opts.bodySerializer === formDataBodySerializer.bodySerializer
            if (["post", "put", "patch"].includes(method) && !headers.has("content-type") && !isFormDataEndpoint) {
                headers.set("content-type", "application/json")
                modified = true
            }

            if (!headers.has("accept")) {
                if (opts.parseAs === "blob") {
                    headers.set("accept", "application/octet-stream")
                    modified = true
                } else if (opts.parseAs === "text") {
                    // Include application/octet-stream: some endpoints (e.g. exportPluginDefaults)
                    // advertise octet-stream in the OpenAPI spec but actually return text.
                    // Kestra content-negotiation returns 403 when Accept excludes the produced type.
                    headers.set("accept", "text/csv, text/plain, text/json, application/json, application/octet-stream")
                    modified = true
                }
            }

            return modified ? new Request(request, {headers}) : request
        })

        // Enrich thrown errors with the HTTP status while preserving Error semantics.
        // The fetch client throws parsed response data (often plain objects/strings),
        // but many existing call sites and tests expect thrown values to be Error instances.
        client.interceptors.error.use((
            error: unknown,
            response: Response | undefined,
            request: Request | undefined,
            opts: ResolvedRequestOptionsLike | undefined,
        ): unknown => {
            if (!response) return error

            const status = response.status

            // An EE-only route 404s on an OSS server the same way a genuinely-missing resource
            // does — matchRoute is what tells the two apart (it only matches the fixed set of
            // EE-only routes, never an arbitrary "flow not found").
            if (status === 404 && enterpriseFeature && request && opts?.url) {
                const match = enterpriseFeature.matchRoute(request.method, opts.url)
                if (match) {
                    return new EnterpriseFeatureError({
                        feature: match.feature,
                        docsUrl: enterpriseFeature.docsUrl(match.feature),
                        contactSalesUrl: enterpriseFeature.contactSalesUrl(match.feature),
                        status,
                    })
                }
            }

            const asObject = error !== null && typeof error === "object" ? error as Record<string, unknown> : undefined
            const rawMessage =
                (error instanceof Error && error.message) ||
                (typeof error === "string" ? error : undefined) ||
                (typeof asObject?.message === "string" ? asObject.message : undefined) ||
                (typeof asObject?.detail === "string" ? asObject.detail : undefined) ||
                (typeof asObject?.title === "string" ? asObject.title : undefined) ||
                response.statusText ||
                "Request failed"

            const hasStatusPrefix =
                rawMessage === String(status) ||
                rawMessage.startsWith(`${status} `) ||
                rawMessage.startsWith(`${status}:`)
            const message = hasStatusPrefix ? rawMessage : `${status} ${rawMessage}`
            const normalizedError = error instanceof Error ? error : new Error(message)
            if (asObject) {
                Object.assign(normalizedError as Error & Record<string, unknown>, asObject)
            }
            normalizedError.message = message
            const normalizedWithStatus = normalizedError as Error & { status: number }
            normalizedWithStatus.status = status
            return normalizedWithStatus
        })

        return client
    }
}
