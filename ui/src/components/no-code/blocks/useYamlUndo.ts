import {ref, type Ref} from "vue"

const UNDO_HISTORY_LIMIT = 100
const UNDO_BADGE_TIMEOUT = 6000
const EDIT_DEBOUNCE = 1000

interface FlowStoreLike {
    flowYaml: string | undefined
    flow?: {namespace?: string; id?: string} | undefined
    onEdit: (payload: {source: string; topologyVisible: boolean}) => void
}

const undoHistory = ref<string[]>([])
const redoHistory = ref<string[]>([])
const historyScope = ref<string | undefined>(undefined)
const undoState = ref<{label: string} | null>(null)
let undoTimer: ReturnType<typeof setTimeout> | undefined

function dismissDeleteBadge() {
    undoState.value = null
    clearTimeout(undoTimer)
}

export function useYamlUndo(flowStore: FlowStoreLike, deletedLabel: (name: string) => string) {
    const onEditTimeout = ref<ReturnType<typeof setTimeout>>()
    let applyingHistory = false

    function enterCurrentScope() {
        const scope = `${flowStore.flow?.namespace ?? ""}/${flowStore.flow?.id ?? ""}`
        if (historyScope.value === scope) return
        historyScope.value = scope
        undoHistory.value = []
        redoHistory.value = []
        dismissDeleteBadge()
    }

    function applyYaml(newYaml: string) {
        enterCurrentScope()
        if (!applyingHistory) {
            const previous = flowStore.flowYaml
            if (typeof previous === "string" && previous !== newYaml) {
                undoHistory.value.push(previous)
                if (undoHistory.value.length > UNDO_HISTORY_LIMIT) undoHistory.value.shift()
                redoHistory.value = []
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

    function applyFromHistory(pushCurrentTo: Ref<string[]>, yaml: string) {
        const current = flowStore.flowYaml
        if (typeof current === "string") {
            pushCurrentTo.value.push(current)
            if (pushCurrentTo.value.length > UNDO_HISTORY_LIMIT) pushCurrentTo.value.shift()
        }
        applyingHistory = true
        try {
            applyYaml(yaml)
        } finally {
            applyingHistory = false
        }
        dismissDeleteBadge()
    }

    function performUndo(): boolean {
        enterCurrentScope()
        if (!undoHistory.value.length) return false
        const previous = undoHistory.value.pop() as string
        applyFromHistory(redoHistory, previous)
        return true
    }

    function performRedo(): boolean {
        enterCurrentScope()
        if (!redoHistory.value.length) return false
        const next = redoHistory.value.pop() as string
        applyFromHistory(undoHistory, next)
        return true
    }

    return {onEditTimeout, undoState, applyYaml, deleteWithUndo, performUndo, performRedo, dismissDeleteBadge}
}
