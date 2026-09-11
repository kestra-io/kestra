import {onMounted, onUnmounted, ref, type Ref} from "vue"

const mounted = ref<symbol[]>([])
const engaged = ref<symbol | undefined>(undefined)

/**
 * Tells two authoring surfaces apart when both are mounted at once. The No-code canvas and the
 * Topology canvas each listen for the same shortcuts on `window`, so in the multi-panel editor a
 * single keypress would otherwise move the selection in both. While only one surface is mounted it
 * stays active with no interaction needed; as soon as a second one appears, a shortcut goes to the
 * surface the user last clicked or focused.
 */
export function useAuthoringSurface(root: Ref<HTMLElement | undefined | null>) {
    const id = Symbol("authoring-surface")

    const engage = (event: Event) => {
        if (root.value?.contains(event.target as Node)) engaged.value = id
    }

    onMounted(() => {
        mounted.value = [...mounted.value, id]
        window.addEventListener("pointerdown", engage, true)
        window.addEventListener("focusin", engage, true)
    })

    onUnmounted(() => {
        mounted.value = mounted.value.filter(entry => entry !== id)
        if (engaged.value === id) engaged.value = undefined
        window.removeEventListener("pointerdown", engage, true)
        window.removeEventListener("focusin", engage, true)
    })

    return {
        isActive: () => mounted.value.length <= 1 || engaged.value === id,
        claim: () => (engaged.value = id),
    }
}
