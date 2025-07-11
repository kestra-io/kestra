export function logout() {
    document.cookie = "BASIC_AUTH=;path=/;expires=Thu, 01 Jan 1970 00:00:01 GMT;samesite=strict";
    return true;
}

export function signIn(username: string, password: string) {
    const trimmedUsername = username.trim();
    const credentials = btoa(`${trimmedUsername}:${password}`)
    document.cookie = `BASIC_AUTH=${credentials};path=/;samesite=strict`;
    return true;
}

export function isLoggedIn() {
    return Boolean(credentials())
}

export function credentials() {
    return document.cookie.split("BASIC_AUTH=")?.[1]?.split(";")?.[0];
}
