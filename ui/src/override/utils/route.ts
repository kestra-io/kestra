import type {Store} from "vuex"

declare global {
    interface Window {
        KESTRA_BASE_PATH: string
    }
}

const createBaseUrl = (): string => {
    const root = (import.meta.env.VITE_APP_API_URL || "") + (window.KESTRA_BASE_PATH || "")
    return root.trim() || window.location.origin
}

export const baseUrl = createBaseUrl().replace(/\/$/, "")
export const basePath = () => "/api/v1/main"

export const apiUrl = (_: Store<any>): string => {
    return `${baseUrl}${basePath}`;
}

export const apiUrlWithoutTenants = (): string => `${baseUrl}/api/v1`
