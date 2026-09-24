import {onActivated, onDeactivated, onMounted, onUnmounted, type Ref} from "vue"

const ALWAYS_GLOBAL_IDS = new Set(["save", "command-menu", "clear", "redo"])
const IGNORES_OVERLAY_GUARD_IDS = new Set(["help"])
// A clipboard shortcut yields to a real text selection, so `⌘C` over selected card text still
// copies the text instead of the block; `isTypingTarget` only covers inputs and Monaco.
const TEXT_SELECTION_GUARDED_IDS = new Set(["copy", "cut", "paste"])
export const AUTHORING_OVERLAY_ATTRIBUTE = "data-authoring-overlay"

export function isTypingTarget(target: EventTarget | null): boolean {
    const el = target as HTMLElement | null
    if (!el) return false
    if (el.tagName === "INPUT" || el.tagName === "TEXTAREA" || el.isContentEditable) return true
    return Boolean(el.closest?.(".monaco-editor"))
}

function hasNonEmptyTextSelection(): boolean {
    const selection = window.getSelection?.()
    return Boolean(selection && !selection.isCollapsed && selection.toString().length > 0)
}

function matchesKey(event: KeyboardEvent, key: string): boolean {
    const parts = key.split("+")
    const mainKey = parts[parts.length - 1]
    const needsMeta = parts.includes("Meta") || parts.includes("Control")
    const needsAlt = parts.includes("Alt")
    const needsShift = parts.includes("Shift")
    if (needsMeta && !(event.metaKey || event.ctrlKey)) return false
    if (!needsMeta && (event.metaKey || event.ctrlKey)) return false
    if (needsAlt && !event.altKey) return false
    if (!needsAlt && event.altKey) return false
    if (needsShift && !event.shiftKey) return false
    const isPlainLetter = /^[a-z]$/i.test(mainKey)
    if (isPlainLetter && !needsShift && event.shiftKey) return false
    return event.key.toLowerCase() === mainKey.toLowerCase()
}

export interface BlockEditorKeyBindingLike {
    id: string
    keys: string[]
    alt?: string[]
}

export interface UseBlockEditorKeyboardOptions {
    keymap: BlockEditorKeyBindingLike[]
    dispatch: (id: string, event: KeyboardEvent) => void | boolean
    isOverlayOpen?: () => boolean
    root?: Ref<HTMLElement | undefined | null>
}

export function resolveBlockEditorBinding(
    event: KeyboardEvent,
    keymap: BlockEditorKeyBindingLike[],
): BlockEditorKeyBindingLike | undefined {
    return keymap.find(binding => [...binding.keys, ...(binding.alt ?? [])].some(key => matchesKey(event, key)))
}

export function useBlockEditorKeyboard(options: UseBlockEditorKeyboardOptions) {
    function handleKeydown(event: KeyboardEvent) {
        const binding = resolveBlockEditorBinding(event, options.keymap)
        if (!binding) return

        if (TEXT_SELECTION_GUARDED_IDS.has(binding.id) && hasNonEmptyTextSelection()) return

        const overlayOpen = options.isOverlayOpen?.() ?? false
        const typing = isTypingTarget(event.target)
        const isGlobal = ALWAYS_GLOBAL_IDS.has(binding.id)
        const ignoresOverlayGuard = IGNORES_OVERLAY_GUARD_IDS.has(binding.id)
        // A field belongs to whoever owns it. The surface's own dialogs are appended to the body,
        // so they are claimed by a marker rather than by DOM containment.
        const foreignTypingTarget =
            typing &&
            options.root?.value != null &&
            !options.root.value.contains(event.target as Node) &&
            !(event.target as HTMLElement | null)?.closest?.(`[${AUTHORING_OVERLAY_ATTRIBUTE}]`)

        if (event.key !== "Escape" && foreignTypingTarget) return
        if (event.key !== "Escape" && !isGlobal && typing) return
        if (event.key !== "Escape" && !isGlobal && !ignoresOverlayGuard && overlayOpen) return

        const handled = options.dispatch(binding.id, event)
        if (handled !== false) event.preventDefault()
    }

    onMounted(() => window.addEventListener("keydown", handleKeydown))
    onUnmounted(() => window.removeEventListener("keydown", handleKeydown))
    onActivated(() => window.addEventListener("keydown", handleKeydown))
    onDeactivated(() => window.removeEventListener("keydown", handleKeydown))

    return {handleKeydown}
}
