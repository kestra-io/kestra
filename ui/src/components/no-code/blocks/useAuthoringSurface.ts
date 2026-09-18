import {onActivated, onDeactivated, onMounted, onUnmounted, ref, type Ref} from "vue"

const active = ref<symbol[]>([])
const engaged = ref<symbol | undefined>(undefined)

export function useAuthoringSurface(root: Ref<HTMLElement | undefined | null>) {
    const id = Symbol("authoring-surface")

    const engage = (event: Event) => {
        if (root.value?.contains(event.target as Node)) engaged.value = id
    }

    const enter = () => {
        if (active.value.includes(id)) return
        active.value = [...active.value, id]
        window.addEventListener("pointerdown", engage, true)
        window.addEventListener("focusin", engage, true)
    }

    const leave = () => {
        active.value = active.value.filter(entry => entry !== id)
        if (engaged.value === id) engaged.value = undefined
        window.removeEventListener("pointerdown", engage, true)
        window.removeEventListener("focusin", engage, true)
    }

    onMounted(enter)
    onActivated(enter)
    onDeactivated(leave)
    onUnmounted(leave)

    function isActive() {
        const current = engaged.value
        if (current !== undefined && active.value.includes(current)) return current === id
        return active.value[active.value.length - 1] === id
    }

    return {
        isActive,
        claim: () => (engaged.value = id),
    }
}
