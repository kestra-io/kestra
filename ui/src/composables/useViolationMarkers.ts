import {onBeforeUnmount, shallowRef, watch, type Ref} from "vue"
import * as monaco from "monaco-editor/editor/editor.api"

import {violationMarkers, type ViolationMarker} from "../utils/violationMarkers"
import type {ValidationError} from "../utils/validationErrors"

const OWNER = "backend-validation"
// Monaco's own error squiggle, so continuation lines look like the marker on the first one.
export const MONACO_ERROR_SQUIGGLE_CLASS = "squiggly-error"

/** Shows backend details verbatim in a markdown hover, like the plain-text marker message. */
function plainText(message: string): monaco.IMarkdownString {
    return {value: message.replace(/[\\`*_{}[\]()#+\-.!|<>~&]/g, "\\$&")}
}

export function useViolationMarkers(options: {
    editor: Ref<monaco.editor.IStandaloneCodeEditor | undefined>
    errors: Ref<ValidationError[] | undefined>
}) {
    // The model outlives its editor (it is cached per tab), so clear the one written to, not the current one.
    let markedModel: monaco.editor.ITextModel | undefined
    let decorations: monaco.editor.IEditorDecorationsCollection | undefined
    const markers = shallowRef<ViolationMarker[]>([])
    let cursor = -1
    let lastFingerprint: string | undefined

    function clear() {
        if (markedModel && !markedModel.isDisposed()) {
            monaco.editor.setModelMarkers(markedModel, OWNER, [])
        }
        markedModel = undefined
        decorations?.clear()
        decorations = undefined
        markers.value = []
    }

    function apply(editor: monaco.editor.IStandaloneCodeEditor | undefined, errors: ValidationError[] | undefined) {
        clear()
        const model = editor?.getModel()
        if (!editor || !model || model.isDisposed()) return
        const resolved = violationMarkers(model.getValue(), errors)
        monaco.editor.setModelMarkers(model, OWNER, resolved.map(({message, lines}) => ({
            ...lines[0],
            message,
            severity: monaco.MarkerSeverity.Error,
        })))
        markedModel = model
        // One Problems entry per violation: the other lines of a block get the same squiggle without a marker.
        decorations = editor.createDecorationsCollection(resolved.flatMap(({message, lines}) => lines.slice(1).map(range => ({
            range,
            options: {className: MONACO_ERROR_SQUIGGLE_CLASS, hoverMessage: plainText(message)},
        }))))
        markers.value = resolved
        // Keep the cursor across a re-resolve that found the same violations, so a click never goes back to error #1.
        const fingerprint = JSON.stringify(resolved)
        if (fingerprint !== lastFingerprint) cursor = -1
        lastFingerprint = fingerprint
    }

    function revealNext() {
        const editor = options.editor.value
        if (!editor || !markers.value.length) return
        cursor = (cursor + 1) % markers.value.length
        const range = markers.value[cursor].lines[0]
        editor.setSelection(range)
        editor.revealRangeInCenterIfOutsideViewport(range, monaco.editor.ScrollType.Smooth)
        editor.focus()
    }

    // Only a new response re-resolves: Monaco shifts existing markers along with the user's edits.
    watch([options.editor, options.errors], ([editor, errors]) => apply(editor, errors), {immediate: true})

    onBeforeUnmount(clear)

    return {markers, revealNext}
}
