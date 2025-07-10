import type {Store} from "vuex"
import * as basicAuth from "../../utils/basicAuth"

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
export const basePath = "/api/v1/main"

export const apiUrl = (_: Store<any>): string => {
    const loginString = basicAuth.getLoginString()

    if (!loginString) return `${baseUrl}${basePath}`

    try {
        const {protocol, host} = new URL(baseUrl)
        return `${protocol}//${loginString}@${host}${basePath}`
    } catch {
        return `${baseUrl}${basePath}`
    }
}

export const apiUrlWithoutTenants = (): string => `${baseUrl}/api/v1`