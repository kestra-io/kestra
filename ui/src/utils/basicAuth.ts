export function logout() {
    document.cookie = "BasicAuth=;expires=Thu, 01 Jan 1970 00:00:01 GMT";
    return true;
}

export function signIn(username: string, password: string) {
    const trimmedUsername = username.trim();
    const credentials = btoa(`${trimmedUsername}:${password}`)
    document.cookie = `BasicAuth=${credentials}`;
    return true;
}

export function isLoggedIn() {
    return Boolean(credentials())
}

export function credentials() {
    return document.cookie.split("BasicAuth=")?.[1]?.split(";")?.[0];
}
