import axios from "axios"
import type { AxiosRequestConfig, AxiosError, AxiosInstance } from "axios"
import { client } from "./openapi/client.gen"
import type { ClientOptions, Config } from "./openapi/client"

export * from "./openapi/index"

function canBeJsonified(str: any): boolean {
    if (typeof str !== "string" && typeof str !== "object") return false;
    try {
        const type = str.toString();
        return type === "[object Object]" || type === "[object Array]";
    } catch {
        return false;
    }
}

function serializeQueryValue(val: unknown) {
    if (canBeJsonified(val)) {
        return JSON.stringify(val);
    }
    return val?.toString();
}

export function configureClient(clientConfig: Config<ClientOptions> = {}, axiosConfig: AxiosRequestConfig = {}) {
    const instance = axios.create(axiosConfig)

    instance.interceptors.request.use((config) => {
        // Axios omits Content-Type for body-less requests, which causes servers that
        // expect application/json (e.g. Kestra) to reject them with 401/415.
        // Force application/json for POST/PUT/PATCH when the body is strictly absent.
        const method = config.method?.toLowerCase()
        if (method === "post" || method === "put" || method === "patch") {
            if (config.data == null) {
                config.headers["Content-Type"] = "application/json"
            }
        }

        // hey-api sets responseType:'blob'/'text' so Axios decodes the response body
        // correctly, but does NOT set the Accept request header. Without an explicit
        // Accept header, Kestra performs content negotiation and may return 404 when
        // it finds no handler matching the client's implicit "Accept: application/json".
        // Note: Axios injects a default "application/json, text/plain, */*" Accept header,
        // so we must override whenever responseType indicates a non-JSON response — not
        // only when the header is absent.
        const axiosDefaultAccept = "application/json, text/plain, */*"
        const currentAccept = config.headers["Accept"]
        const hasCustomAccept = currentAccept && currentAccept !== axiosDefaultAccept
        if (!hasCustomAccept) {
            if (config.responseType === "blob") {
                config.headers["Accept"] = "application/octet-stream, text/plain, */*"
            } else if (config.responseType === "text") {
                config.headers["Accept"] = "text/csv, text/plain, text/json, application/json"
            }
        }
        return config
    })

    client.setConfig({
        axios: instance,
        querySerializer(query) {
            const queryParameters = new URLSearchParams();
            for (const key in query) {
                const param = query[key];
                if (query[key] === undefined) {
                    continue
                }
                const looksLikeQueryFilterArray =
                    Array.isArray(param) &&
                    param.length > 0 &&
                    typeof param[0] === "object" &&
                    param[0] != null &&
                    "field" in param[0] &&
                    "operation" in param[0] &&
                    "value" in param[0];

                if (looksLikeQueryFilterArray) {
                    for (const qf of param) {
                        const keyField = String(qf.field);
                        const op = String(qf.operation);

                        if (
                            typeof qf.value === "object" &&
                            qf.value != null &&
                            !Array.isArray(qf.value)
                        ) {
                            for (const [k, v] of Object.entries(qf.value)) {
                                const ser = serializeQueryValue(v);
                                if (ser !== undefined) {
                                    queryParameters.append(`filters[${keyField}][${op}][${k}]`, ser);
                                }
                            }
                        } else {
                            const ser = serializeQueryValue(
                                qf.value,
                            );
                            if (ser !== undefined) {
                                queryParameters.append(`filters[${keyField}][${op}]`, ser);
                            }
                        }
                    }
                } else if (param instanceof Array) {
                    param.forEach((value: any) => {
                        const ser = serializeQueryValue(value)
                        if (ser !== undefined) {
                            queryParameters.append(key, ser);
                        }
                    });
                } else {
                    const ser = serializeQueryValue(param)
                    if (ser !== undefined) {
                        queryParameters.append(key, ser);
                    }
                }
            }
            return queryParameters.toString();
        },
        ...clientConfig,
    })

    // When responseType:'blob' is set (e.g. for AI generate endpoints that return YAML),
    // Axios may parse the error response body as a Blob (browser) or raw string (Node.js)
    // instead of JSON, hiding the real server error message. Normalise it so callers see
    // the actual error object (e.g. { message: "BAD_REQUEST: ..." }) in both environments.
    instance.interceptors.response.use(undefined, async (error: AxiosError) => {
        if (error.response?.data instanceof Blob) {
            const text = await (error.response.data as Blob).text()
            try {
                error.response.data = JSON.parse(text)
            } catch {
                error.response.data = text
            }
        } else if (typeof error.response?.data === 'string' && error.response.data.length > 0) {
            try {
                error.response.data = JSON.parse(error.response.data)
            } catch {
                // not JSON, leave as-is
            }
        }
        return Promise.reject(error)
    })

    instance.defaults.paramsSerializer = {
        indexes: null
    };

    axiosInstance = instance

    return instance
}

let axiosInstance: AxiosInstance | null = null;

/**
 * Set a mock instance of axios controlled in tests
 * @param mockClient
 */
export function setMockClient(mockClient: any) {
    axiosInstance = mockClient;
}

/**
 * Get the current Axios client instance
 * @returns AxiosInstance
 */
export function useClient(): AxiosInstance {
    return new Proxy({} as AxiosInstance, {
        get(_target, prop) {
            if (!axiosInstance) {
                throw new Error("Axios instance not initialized. Please call configureClient first.")
            }
            const value = (axiosInstance as any)[prop]
            return typeof value === "function" ? value.bind(axiosInstance) : value
        }
    })
}
