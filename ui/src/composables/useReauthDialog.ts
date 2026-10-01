import {readonly, ref} from "vue"

const visible = ref(false)
const loginUrl = ref<string>()
const canSignInWithPassword = ref(false)
let pending: Promise<boolean> | undefined
let settle: ((signedIn: boolean) => void) | undefined
let signInHandler: SignIn | undefined

export type SignIn = (credentials: {username: string, password: string}) => Promise<unknown>

export interface ReauthOptions {
    /** Shows the username and password form when set. */
    signIn?: SignIn
    /** Lets the user sign in a new tab, for logins that cannot happen in the dialog (SSO, passwordless). */
    loginUrl?: string
}

/** Concurrent callers share one dialog, one outcome and the options of the first caller. */
export function requestReauth(options: ReauthOptions): Promise<boolean> {
    pending ??= new Promise<boolean>((resolve) => {
        signInHandler = options.signIn
        canSignInWithPassword.value = Boolean(options.signIn)
        loginUrl.value = options.loginUrl
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
    return {visible: readonly(visible), loginUrl: readonly(loginUrl), canSignInWithPassword: readonly(canSignInWithPassword)}
}
