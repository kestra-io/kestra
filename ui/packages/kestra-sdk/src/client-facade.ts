import type {ResolvedRequestOptions} from "./openapi/client"

// App-only half of a Kestra SDK: the axios-like fetch facade + the useClient()/setMockClient()
// accessors. This is intentionally NOT in the shared @kestra-io/hey-api-plugin package and NOT part
// of the public generated SDK — only the apps (OSS/EE) expose useClient()/setMockClient().
//
// It is a client-AGNOSTIC factory: `createClientFacade(client, formDataBodySerializer)` binds the
// facade to a given @hey-api/client-fetch client. OSS calls it with the OSS client; EE imports this
// exact module by relative path and calls it with the EE client — so the ~130 lines of facade logic
// live in ONE place (DRY) while each SDK stays bound to its own client. useClient() returns a facade
// that shares the SAME `client.interceptors` that createConfigureClient() installs, so ad-hoc
// useClient().get/post(...) calls behave identically to generated endpoint calls, and existing
// OSS/EE call sites are unchanged.

export interface AxiosLikeConfig {
    params?: Record<string, unknown>
    headers?: Record<string, string>
    responseType?: "json" | "text" | "blob"
    timeout?: number
    validateStatus?: (status: number) => boolean
    [key: string]: any
}

export interface AxiosLikeResponse<T = any> {
    data: T
    status: number
    headers: Record<string, string>
    // Optional so existing setMockClient()/test mocks aren't forced to fabricate one.
    request?: { responseURL: string }
}

export interface StreamConfig {
    headers?: Record<string, string>
    signal?: AbortSignal
}

/** Minimal shape of the @hey-api/client-fetch client this facade reads. */
interface InterceptedFetchClient {
    interceptors: {
        request: { fns: Array<((request: Request, options: any) => Request | Promise<Request>) | null> }
        response: { fns: Array<((response: Response, request: Request, options: any) => Response | Promise<Response>) | null> }
        error: { fns: Array<((error: unknown, response: Response, request: Request, options: any) => unknown) | null> }
    }
}

interface FormDataBodySerializer {
    bodySerializer: (...args: any[]) => any
}


function withQuery(url: string, params?: Record<string, unknown>): string {
    if (!params) return url
    const search = new URLSearchParams()
    for (const [key, value] of Object.entries(params)) {
        if (value === undefined || value === null) continue
        if (Array.isArray(value)) {
            value.forEach(v => search.append(key, String(v)))
        } else {
            search.append(key, typeof value === "object" ? JSON.stringify(value) : String(value))
        }
    }
    const query = search.toString()
    if (!query) return url
    return url.includes("?") ? `${url}&${query}` : `${url}?${query}`
}

export interface ClientFacade {
    useClient: () => AxiosLikeClient
    setMockClient: (mockClient?: Partial<AxiosLikeClient>) => void
}

export interface AxiosLikeClient {
    defaults: { headers: { common: Record<string, string> } }
    get: <T = any>(url: string, config?: AxiosLikeConfig) => Promise<AxiosLikeResponse<T>>
    post: <T = any>(url: string, data?: any, config?: AxiosLikeConfig) => Promise<AxiosLikeResponse<T>>
    put: <T = any>(url: string, data?: any, config?: AxiosLikeConfig) => Promise<AxiosLikeResponse<T>>
    delete: <T = any>(url: string, config?: AxiosLikeConfig) => Promise<AxiosLikeResponse<T>>
    patch: <T = any>(url: string, data?: any, config?: AxiosLikeConfig) => Promise<AxiosLikeResponse<T>>
    /**
     * POSTs `data` and resolves with the RAW `Response`, body unconsumed, so callers can read it
     * incrementally — e.g. POST-based SSE streams, which `EventSource` cannot issue. Runs the same
     * shared request/response interceptors as the axios-like methods (CSRF header, progress, any
     * EE additions), so streaming endpoints never need to reimplement that cross-cutting logic.
     * Unlike the axios-like methods, a non-2xx response is RETURNED, not thrown, and error
     * interceptors are NOT run: streaming callers own their error UX (no global toasts/redirects).
     */
    stream: (url: string, data?: any, config?: StreamConfig) => Promise<Response>
}

export function createClientFacade(
    client: InterceptedFetchClient,
    formDataBodySerializer: FormDataBodySerializer,
): ClientFacade {
    const commonHeaders: Record<string, string> = {}

    /** Shares client.interceptors.* with configureClient() so ad-hoc useClient() calls get the same behavior as generated endpoint calls. */
    async function axiosLikeRequest<T>(
        method: string,
        url: string,
        data?: any,
        config: AxiosLikeConfig = {},
    ): Promise<AxiosLikeResponse<T>> {
        const fullUrl = withQuery(url, config.params)
        const headers = new Headers({...commonHeaders, ...(config.headers ?? {})})
        const isFormData = data instanceof FormData

        let body: BodyInit | undefined
        if (isFormData || data instanceof Blob) {
            body = data
        } else if (data !== undefined) {
            if (typeof data === "string") {
                body = data
            } else {
                body = JSON.stringify(data)
                if (!headers.has("content-type")) {
                    headers.set("content-type", "application/json")
                }
            }
        }

        const requestInit: RequestInit = {method, headers, body, credentials: "include", redirect: "follow"}
        if (config.timeout) requestInit.signal = AbortSignal.timeout(config.timeout)

        // bodySerializer identity marks a multipart endpoint for the shared request interceptor.
        const interceptorOptions = {
            ...config,
            bodySerializer: isFormData ? formDataBodySerializer.bodySerializer : undefined,
            parseAs: config.responseType,
        } as unknown as ResolvedRequestOptions

        let request = new Request(fullUrl, requestInit)
        for (const fn of client.interceptors.request.fns) {
            if (fn) request = await fn(request, interceptorOptions)
        }

        let response = await fetch(request)
        for (const fn of client.interceptors.response.fns) {
            if (fn) response = await fn(response, request, interceptorOptions)
        }

        const {validateStatus} = config
        const isSuccess = validateStatus ? validateStatus(response.status) : response.status < 400

        const headersObj: Record<string, string> = {}
        response.headers.forEach((value, key) => { headersObj[key] = value })

        if (!isSuccess) {
            const textError = await response.text()
            let parsedError: unknown
            try {
                parsedError = JSON.parse(textError)
            } catch {
                parsedError = textError
            }

            let finalError: unknown = parsedError
            for (const fn of client.interceptors.error.fns) {
                if (fn) finalError = await fn(finalError, response, request, interceptorOptions)
            }
            if (finalError && typeof finalError === "object") {
                (finalError as Record<string, unknown>).response = {
                    data: parsedError, status: response.status, headers: headersObj, request: {responseURL: response.url},
                }
            }
            throw finalError
        }

        let responseData: T
        if (config.responseType === "blob") {
            responseData = await response.blob() as T
        } else if (response.status === 204 || response.headers.get("content-length") === "0") {
            responseData = null as T
        } else if (config.responseType !== "text" && (response.headers.get("content-type") ?? "").includes("application/json")) {
            responseData = await response.json() as T
        } else {
            responseData = await response.text() as unknown as T
        }

        return {data: responseData, status: response.status, headers: headersObj, request: {responseURL: response.url}}
    }

    /** See {@link AxiosLikeClient.stream} — raw-Response variant of axiosLikeRequest for streaming endpoints. */
    async function streamRequest(url: string, data?: any, config: StreamConfig = {}): Promise<Response> {
        const headers = new Headers({...commonHeaders, ...(config.headers ?? {})})
        let body: BodyInit | undefined
        if (data !== undefined) {
            if (typeof data === "string") {
                body = data
            } else {
                body = JSON.stringify(data)
                if (!headers.has("content-type")) {
                    headers.set("content-type", "application/json")
                }
            }
        }

        const interceptorOptions = {...config} as unknown as ResolvedRequestOptions
        let request = new Request(url, {method: "POST", headers, body, credentials: "include", redirect: "follow", signal: config.signal})
        for (const fn of client.interceptors.request.fns) {
            if (fn) request = await fn(request, interceptorOptions)
        }

        let response = await fetch(request)
        for (const fn of client.interceptors.response.fns) {
            if (fn) response = await fn(response, request, interceptorOptions)
        }
        return response
    }

    const axiosLikeClient: AxiosLikeClient = {
        defaults: {headers: {common: commonHeaders}},
        get: <T = any>(url: string, config?: AxiosLikeConfig) => axiosLikeRequest<T>("GET", url, undefined, config),
        post: <T = any>(url: string, data?: any, config?: AxiosLikeConfig) => axiosLikeRequest<T>("POST", url, data, config),
        put: <T = any>(url: string, data?: any, config?: AxiosLikeConfig) => axiosLikeRequest<T>("PUT", url, data, config),
        delete: <T = any>(url: string, config?: AxiosLikeConfig) => axiosLikeRequest<T>("DELETE", url, config?.data, config),
        patch: <T = any>(url: string, data?: any, config?: AxiosLikeConfig) => axiosLikeRequest<T>("PATCH", url, data, config),
        stream: streamRequest,
    }

    /** Set a mock client instance controlled in tests. */
    function setMockClient(mockClient: Partial<AxiosLikeClient> = {}) {
        for (const method of ["get", "post", "put", "delete", "patch", "stream"] as const) {
            if (mockClient[method]) {
                (axiosLikeClient as any)[method] = mockClient[method] as any
            }
        }
    }

    /** Get the current fetch client instance (the axios-like facade). */
    function useClient(): AxiosLikeClient {
        return axiosLikeClient
    }

    return {useClient, setMockClient}
}
