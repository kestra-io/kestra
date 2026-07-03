import {apiUrlWithoutTenants} from "override/utils/route"
import {getCsrfToken} from "./csrf"

// The BASIC_AUTH cookie itself is HttpOnly and issued by the server (see MiscController#login/#logout);
// this flag never carries credentials, it only mirrors client-side whether a login round-trip succeeded.
const AUTH_FLAG_KEY = "kestraBasicAuthenticated"

export async function logout() {
    sessionStorage.removeItem(AUTH_FLAG_KEY)
    try {
        await fetch(`${apiUrlWithoutTenants()}/logout`, {
            method: "POST",
            credentials: "include",
            headers: {"X-CSRF-TOKEN": getCsrfToken() ?? ""},
        })
    } catch {
        // best-effort: the local flag is already cleared, next API call will 401 if the cookie is still valid server-side
    }
    return true
}

export function signIn() {
    sessionStorage.setItem(AUTH_FLAG_KEY, "true")
    return true
}

export function isLoggedIn() {
    return sessionStorage.getItem(AUTH_FLAG_KEY) === "true"
}
