import {readonly, ref} from "vue"

const unreachable = ref(false)

export function markServerUnreachable() {
    unreachable.value = true
}

export function markServerReachable() {
    unreachable.value = false
}

export function useServerReachability() {
    return {unreachable: readonly(unreachable)}
}
