import {onBeforeUnmount, watch, type Ref} from "vue"
import * as monaco from "monaco-editor/editor/editor.api"

import {violationMarkers, type LocatedViolation} from "../utils/violationMarkers"

const OWNER = "backend-validation"

export function useViolationMarkers(options: {
    editor: Ref<monaco.editor.IStandaloneCodeEditor | undefined>
    violations: Ref<LocatedViolation[] | undefined>
}) {
    function apply(editor: monaco.editor.IStandaloneCodeEditor | undefined, violations: LocatedViolation[] | undefined) {
        const model = editor?.getModel()
        if (!model) return
        monaco.editor.setModelMarkers(model, OWNER, violationMarkers(model.getValue(), violations).map(marker => ({
            ...marker,
            severity: monaco.MarkerSeverity.Error,
        })))
    }

    // Only a new response re-resolves: Monaco shifts existing markers along with the user's edits.
    watch([options.editor, options.violations], ([editor, violations]) => apply(editor, violations), {immediate: true})

    onBeforeUnmount(() => apply(options.editor.value, undefined))
}
