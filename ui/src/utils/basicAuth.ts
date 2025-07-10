export function logout() {
    localStorage.removeItem("basicAuthCredentials");
    localStorage.removeItem("basicAuthLogin");
    localStorage.removeItem("basicAuthPassword");
    return true;
}

export function signIn(username: string, password: string) {
    const trimmedUsername = username.trim();
    const credentials = btoa(`${trimmedUsername}:${password}`)
    localStorage.setItem("basicAuthCredentials", credentials)
    localStorage.setItem("basicAuthLogin", trimmedUsername)
    localStorage.setItem("basicAuthPassword", password)
    return true;
}

export function isLoggedIn() {
    return Boolean(credentials())
}

export function credentials() {
    return localStorage.getItem("basicAuthCredentials");
}

export function getLoginString() {
    const login = localStorage.getItem("basicAuthLogin");
    const password = localStorage.getItem("basicAuthPassword");
    if (!login || !password) {
        return null;
    }
    return `${login}:${password}`;
}