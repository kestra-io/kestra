import {Store} from "vuex";

declare global {
    interface Window {
        KESTRA_BASE_PATH: string
    }
}

let root = (import.meta.env.VITE_APP_API_URL || "") + window.KESTRA_BASE_PATH;
if (root.endsWith("/")) {
    root = root.substring(0, root.length - 1);
}

export const baseUrl = root;

export const basePath = () => "/api/v1/main"

export const apiUrl = (_: Store<any>) => {
    const login = localStorage.getItem("basicAuthLogin");
    const password = localStorage.getItem("basicAuthPassword");
    if(!login || !password) {
        return `${baseUrl}${basePath()}`
    }
    const baseUrlParsed = new URL(baseUrl); // Validate baseUrl
    return `${baseUrlParsed.protocol}//${login}:${password}@${baseUrlParsed.host}${basePath()}`
}

export const apiUrlWithoutTenants = () => `${baseUrl}/api/v1`