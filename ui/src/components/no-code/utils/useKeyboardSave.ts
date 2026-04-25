import {onActivated, onDeactivated} from "vue"
import {useFlowStore} from "../../../stores/flow"

export function useKeyboardSave() {
    const flowStore = useFlowStore()
    const handleKeyboardSave = (e: KeyboardEvent) => {
        if (e.type === "keydown" && e.key === "s" && (e.ctrlKey || e.metaKey)) {
            e.preventDefault()
            // Ctrl+S follows the user's default save action preference (Save vs Save as draft),
            // matching what the split-button dropdown shows.
            flowStore.saveWithDefaultAction()
        }
    }

    onActivated(() => {
        document.addEventListener("keydown", handleKeyboardSave)
    })


    onDeactivated(() => {
        document.removeEventListener("keydown", handleKeyboardSave)
    })
}