import {readonly, ref} from "vue"

export type SignIn = (credentials: {username: string, password: string}) => Promise<unknown>

export interface ReauthOptions {
    signIn?: SignIn
    loginUrl?: string
    confirm?: () => Promise<unknown>
    username?: string
}

const visible = ref(false)
const loginUrl = ref<string>()
const lockedUsername = ref<string>()
const canSignInWithPassword = ref(false)
let pending: Promise<boolean> | undefined
let settle: ((signedIn: boolean) => void) | undefined
let current: ReauthOptions = {}
let rechecking = false

export function requestReauth(options: ReauthOptions): Promise<boolean> {
    pending ??= new Promise<boolean>((resolve) => {
        current = options
        canSignInWithPassword.value = Boolean(options.signIn)
        loginUrl.value = options.loginUrl
        lockedUsername.value = options.username
        settle = (signedIn) => {
            window.removeEventListener("focus", recheckReauth)
            pending = undefined
            settle = undefined
            current = {}
            visible.value = false
            resolve(signedIn)
        }
        visible.value = true
        window.addEventListener("focus", recheckReauth)
    })
    return pending
}

export async function submitReauth(credentials: {username: string, password: string}) {
    if (!current.signIn) throw new Error("No password sign-in is pending.")
    await current.signIn(credentials)
    await current.confirm?.()
}

export async function confirmReauth() {
    await current.confirm?.()
}

export async function recheckReauth() {
    const settleOpened = settle
    if (!settleOpened || !current.confirm || rechecking) return
    rechecking = true
    try {
        await current.confirm()
        if (settle === settleOpened) settleOpened(true)
    } catch {
        // still signed out: the dialog stays open
    } finally {
        rechecking = false
    }
}

export function resolveReauth(signedIn: boolean) {
    settle?.(signedIn)
}

export function isReauthOpen() {
    return visible.value
}

export function useReauthDialog() {
    return {
        visible: readonly(visible),
        loginUrl: readonly(loginUrl),
        lockedUsername: readonly(lockedUsername),
        canSignInWithPassword: readonly(canSignInWithPassword),
    }
}
