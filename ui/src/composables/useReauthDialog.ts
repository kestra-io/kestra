import {readonly, ref} from "vue"

const visible = ref(false)
let pending: Promise<boolean> | undefined
let settle: ((signedIn: boolean) => void) | undefined
let signInHandler: SignIn | undefined

export type SignIn = (credentials: {username: string, password: string}) => Promise<unknown>

/** Concurrent callers share one dialog, one outcome and the sign-in of the first caller. */
export function requestReauth(signIn: SignIn): Promise<boolean> {
    pending ??= new Promise<boolean>((resolve) => {
        signInHandler = signIn
        settle = (signedIn) => {
            pending = undefined
            settle = undefined
            signInHandler = undefined
            visible.value = false
            resolve(signedIn)
        }
        visible.value = true
    })
    return pending
}

export function submitReauth(credentials: {username: string, password: string}) {
    return signInHandler?.(credentials) ?? Promise.reject(new Error("No re-authentication is pending."))
}

export function resolveReauth(signedIn: boolean) {
    settle?.(signedIn)
}

export function isReauthOpen() {
    return visible.value
}

export function useReauthDialog() {
    return {visible: readonly(visible)}
}
