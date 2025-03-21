
let _root = null

export const baseUrl = () => {
    if(_root === null) {
        _root = (import.meta.env.VITE_APP_API_URL || "") + window.KESTRA_BASE_PATH;
        if (_root.endsWith("/")) {
            _root = _root.substring(0, _root.length - 1);
        }
        if (!window.KESTRA_BASE_PATH) {
            throw new Error("Root not defined")
        }
    }
    return _root
};

export const basePath = () => "/api/v1"

export const apiUrl = () => `${baseUrl()}${basePath()}`

export const apiUrlWithoutTenants = () => `${baseUrl()}/api/v1`