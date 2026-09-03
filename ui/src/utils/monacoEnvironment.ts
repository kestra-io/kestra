import EditorWorker from "monaco-editor/editor/editor.worker?worker"
import JsonWorker from "monaco-editor/language/json/json.worker?worker"
import TypeScriptWorker from "monaco-editor/language/typescript/ts.worker?worker"
import YamlWorker from "../components/inputs/yaml.worker.js?worker"

let nodeTypesRequested = false

// Lazily registers @types/node on first JS/TS worker request.
function loadNodeTypes() {
    Promise.all([
        import("monaco-editor/languages/features/typescript/register"),
        import("./monacoNodeTypes"),
    ]).then(([{typescriptDefaults}, {default: nodeTypes}]) => {
        for (const path in nodeTypes) {
            typescriptDefaults.addExtraLib(nodeTypes[path], `file://${path}`)
        }
    })
}

window.MonacoEnvironment = {
    getWorker(_moduleId, label) {
        switch (label) {
        case "editorWorkerService":
            return new EditorWorker()
        case "yaml":
            return new YamlWorker()
        case "json":
            return new JsonWorker()
        case "javascript":
        case "typescript":
            if (!nodeTypesRequested) {
                nodeTypesRequested = true
                loadNodeTypes()
            }
            return new TypeScriptWorker()
        default:
            throw new Error(`Unknown label ${label}`)
        }
    },
}
