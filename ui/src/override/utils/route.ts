import {RouteLocationNormalizedLoaded} from "vue-router";

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
export const basePathWithoutTenant = () => "/api/v1"

export const apiUrl = (): string => {
    return `${baseUrl}${basePath()}`;
}

export const apiUrlWithTenant = (_: RouteLocationNormalizedLoaded): string => apiUrl();
export const apiUrlWithoutTenants = (): string => `${baseUrl}${basePathWithoutTenant()}`;
