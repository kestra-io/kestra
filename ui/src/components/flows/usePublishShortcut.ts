import {onMounted, onUnmounted} from "vue"
import {useFlowEditorActions} from "./useFlowEditorActions"

// Edge and Firefox keep Ctrl+Shift+S for screen captures; Alt changes the typed character, hence the physical key, and AltGr typing is left alone.
function isPublishChord(event: KeyboardEvent) {
    return (event.ctrlKey || event.metaKey)
        && event.altKey
        && !event.shiftKey
        && !event.getModifierState?.("AltGraph")
        && event.code === "KeyS"
}

export function usePublishShortcut() {
    const {publish, isReadOnly, hasErrors} = useFlowEditorActions()

    function onKeydown(event: KeyboardEvent) {
        if (!isPublishChord(event)) return
        event.preventDefault()
        if (!event.repeat && !isReadOnly.value && !hasErrors.value) {
            publish()
        }
    }

    onMounted(() => window.addEventListener("keydown", onKeydown))
    onUnmounted(() => window.removeEventListener("keydown", onKeydown))
}
