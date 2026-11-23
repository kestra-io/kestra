export class PublicApiError extends Error {
    constructor(readonly status: number, url: string) {
        super(`The request to '${url}' failed with status ${status}.`)
        this.name = "PublicApiError"
    }
}

export interface PublicFetchOptions {
    params?: Record<string, string | undefined>;
    signal?: AbortSignal;
    withCredentials?: boolean;
    body?: unknown;
}

/** Fetches kestra.io's public API, outside the Kestra client so no CSRF or reauth handling reaches a third party. Throws {@link PublicApiError} on a non-2xx. */
export async function publicFetch(url: string, {params, signal, withCredentials, body}: PublicFetchOptions = {}): Promise<Response> {
    const target = new URL(url)
    for (const [key, value] of Object.entries(params ?? {})) {
        if (value !== undefined) target.searchParams.set(key, value)
    }

    const response = await fetch(target, {
        method: body === undefined ? "GET" : "POST",
        headers: body === undefined ? undefined : {"Content-Type": "application/json"},
        body: body === undefined ? undefined : JSON.stringify(body),
        credentials: withCredentials ? "include" : "same-origin",
        signal,
    })
    if (!response.ok) throw new PublicApiError(response.status, url)
    return response
}

export async function publicFetchJson<T>(url: string, options?: PublicFetchOptions): Promise<T> {
    return await (await publicFetch(url, options)).json() as T
}
