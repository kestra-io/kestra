import {readonly, ref} from "vue"

const visible = ref(false)
let pending: Promise<boolean> | undefined
let settle: ((signedIn: boolean) => void) | undefined

/** Concurrent callers share one dialog and one outcome. */
export function requestReauth(): Promise<boolean> {
    pending ??= new Promise<boolean>((resolve) => {
        settle = (signedIn) => {
            pending = undefined
            settle = undefined
            visible.value = false
            resolve(signedIn)
        }
        visible.value = true
    })
    return pending
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
