import {apiUrlWithoutTenants} from "override/utils/route"
import {getCsrfToken} from "./csrf"
import {useClient} from "@kestra-io/kestra-sdk"

// The BASIC_AUTH cookie itself is HttpOnly and issued by the server (see MiscController#login/#logout).
// The server also issues this non-HttpOnly cookie in lockstep, mirroring whether BASIC_AUTH is set;
const AUTH_FLAG_COOKIE_NAME = "kestraBasicAuthenticated"

export async function logout() {
    try {
        await fetch(`${apiUrlWithoutTenants()}/logout`, {
            method: "POST",
            credentials: "include",
            headers: {"X-CSRF-TOKEN": getCsrfToken() ?? ""},
        })
    } catch {
        // best-effort: if this fails, the cookies (and thus isLoggedIn()) remain as the server last set them
    }
    return true
}

export async function signIn(credentials: {username: string, password: string}) {
    const {username, password} = credentials
    const trimmedUsername = username.trim()
    await validateCredentials(trimmedUsername, password)
    return {username: trimmedUsername}
}

export function isLoggedIn() {
    return document.cookie
        .split("; ")
        .includes(`${AUTH_FLAG_COOKIE_NAME}=true`)
}

async function validateCredentials(username: string, password: string) {
    try {
        const axios = useClient()
        await axios.post(`${apiUrlWithoutTenants()}/login`, {username, password}, {timeout: 10000, withCredentials: true})
    } catch(e) {
        await logout()
        throw e
    }
}
