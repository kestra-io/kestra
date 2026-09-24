import {onActivated, onDeactivated, onMounted, onUnmounted, ref, type Ref} from "vue"
import {AUTHORING_OVERLAY_ATTRIBUTE, isTypingTarget} from "./useBlockEditorKeyboard"

const active = ref<symbol[]>([])
const engaged = ref<symbol | undefined>(undefined)
const roots = new Map<symbol, Ref<HTMLElement | undefined | null>>()

/**
 * Whether an authoring surface will answer a shortcut raised on this target. Another window
 * listener for the same chord has to ask rather than infer: a surface's root and its
 * `[data-authoring-overlay]` dialogs are not visible from outside, and a target belonging to
 * neither is one the surface stands down for.
 */
export function authoringSurfaceAnswersKeyFor(target: Node | null): boolean {
    if (active.value.length === 0) return false
    if (!target) return true
    if ((target as HTMLElement).closest?.(`[${AUTHORING_OVERLAY_ATTRIBUTE}]`)) return true
    if (active.value.some(id => roots.get(id)?.value?.contains(target))) return true
    // Mirrors the rule in useBlockEditorKeyboard: a surface only stands down for a *typing*
    // target it does not own. A chord raised on nothing in particular belongs to the canvas.
    return !isTypingTarget(target)
}

export function useAuthoringSurface(root: Ref<HTMLElement | undefined | null>) {
    const id = Symbol("authoring-surface")

    const engage = (event: Event) => {
        if (root.value?.contains(event.target as Node)) engaged.value = id
    }

    const enter = () => {
        if (active.value.includes(id)) return
        roots.set(id, root)
        active.value = [...active.value, id]
        window.addEventListener("pointerdown", engage, true)
        window.addEventListener("focusin", engage, true)
    }

    const leave = () => {
        roots.delete(id)
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
