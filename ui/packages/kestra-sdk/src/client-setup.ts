import type { AxiosInstance } from "axios"

// App-only (OSS/EE) singleton + accessors for "the current configured client".
//
// Block-1 — the universal axios setup (createConfigureClient) — now lives in
// @kestra-io/hey-api-plugin/runtime and is shared by every SDK. This file is the other half the
// user identified: the app's global-instance convenience + test seam. It is intentionally NOT part
// of the shared package or the public SDK — only the apps use useClient() / setMockClient().

let axiosInstance: AxiosInstance | null = null;

/** Record the instance built by configureClient so useClient() can return it. */
export function rememberInstance(instance: AxiosInstance) {
    axiosInstance = instance;
}

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
