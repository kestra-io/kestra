
let root = (import.meta.env.VITE_APP_API_URL || "") + window.KESTRA_BASE_PATH;
if (root.endsWith("/")) {
    root = root.substring(0, root.length - 1);
}

export const baseUrl = root;

export const basePath = () => "/api/v1"

export const apiUrl = () => `${baseUrl}${basePath()}`

export const apiUrlWithoutTenants = () => `${baseUrl}/api/v1`