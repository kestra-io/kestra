import {onActivated, onDeactivated} from "vue"
import {useFlowEditorActions} from "../../flows/useFlowEditorActions"

export function useKeyboardSave() {
    const {saveKeepingDraftState} = useFlowEditorActions()
    const handleKeyboardSave = (e: KeyboardEvent) => {
        if (e.type === "keydown" && e.key === "s" && (e.ctrlKey || e.metaKey)) {
            e.preventDefault()
            saveKeepingDraftState()
        }
    }

    onActivated(() => {
        document.addEventListener("keydown", handleKeyboardSave)
    })


    onDeactivated(() => {
        document.removeEventListener("keydown", handleKeyboardSave)
    })
}