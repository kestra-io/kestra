import {computed, watch, type Ref} from "vue"
import {useRoute} from "vue-router"
import type * as monaco from "monaco-editor/editor/editor.api"

export function parseRouteLine(value: unknown): number | undefined {
    const line = Number(Array.isArray(value) ? value[0] : value)
    return Number.isInteger(line) && line > 0 ? line : undefined
}

export function useRevealRouteLine(editor: Ref<monaco.editor.IStandaloneCodeEditor | undefined>, source: Ref<string | undefined>, enabled: Ref<boolean>) {
    const route = useRoute()
    const requestedLine = computed(() => enabled.value ? parseRouteLine(route.query.line) : undefined)
    let revealed: string | undefined

    function reveal() {
        const line = requestedLine.value
        const model = editor.value?.getModel()
        if (!line || !editor.value || !model || model.getLineCount() < line) return
        if (revealed === route.fullPath) return
        revealed = route.fullPath
        editor.value.focus()
        editor.value.setSelection({startLineNumber: line, startColumn: 1, endLineNumber: line, endColumn: model.getLineMaxColumn(line)})
        editor.value.revealLineInCenter(line)
    }

    watch([requestedLine, editor, source], reveal, {flush: "post", immediate: true})
}
