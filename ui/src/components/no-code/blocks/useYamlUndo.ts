import {ref} from "vue"

const UNDO_HISTORY_LIMIT = 100
const UNDO_BADGE_TIMEOUT = 6000
const EDIT_DEBOUNCE = 1000

interface FlowStoreLike {
    flowYaml: string | undefined
    onEdit: (payload: {source: string; topologyVisible: boolean}) => void
}

export function useYamlUndo(flowStore: FlowStoreLike, deletedLabel: (name: string) => string) {
    const onEditTimeout = ref<ReturnType<typeof setTimeout>>()
    const undoHistory = ref<string[]>([])
    const undoState = ref<{label: string} | null>(null)
    let undoTimer: ReturnType<typeof setTimeout> | undefined
    let applyingUndo = false

    function dismissDeleteBadge() {
        undoState.value = null
        clearTimeout(undoTimer)
    }

    function applyYaml(newYaml: string) {
        if (!applyingUndo) {
            const previous = flowStore.flowYaml
            if (typeof previous === "string" && previous !== newYaml) {
                undoHistory.value.push(previous)
                if (undoHistory.value.length > UNDO_HISTORY_LIMIT) undoHistory.value.shift()
            }
            dismissDeleteBadge()
        }
        flowStore.flowYaml = newYaml
        clearTimeout(onEditTimeout.value)
        onEditTimeout.value = setTimeout(() => {
            flowStore.onEdit({source: newYaml, topologyVisible: true})
        }, EDIT_DEBOUNCE)
    }

    function deleteWithUndo(name: string, mutate: () => void) {
        mutate()
        undoState.value = {label: deletedLabel(name)}
        clearTimeout(undoTimer)
        undoTimer = setTimeout(dismissDeleteBadge, UNDO_BADGE_TIMEOUT)
    }

    function performUndo() {
        if (!undoHistory.value.length) return
        const previous = undoHistory.value.pop() as string
        applyingUndo = true
        try {
            applyYaml(previous)
        } finally {
            applyingUndo = false
        }
        dismissDeleteBadge()
    }

    return {onEditTimeout, undoState, applyYaml, deleteWithUndo, performUndo, dismissDeleteBadge}
}
