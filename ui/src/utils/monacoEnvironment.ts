import EditorWorker from "monaco-editor/esm/vs/editor/editor.worker?worker"
import JsonWorker from "monaco-editor/esm/vs/language/json/json.worker?worker"
import TypeScriptWorker from "monaco-editor/esm/vs/language/typescript/ts.worker?worker"
import YamlWorker from "../components/inputs/yaml.worker.js?worker"

let nodeTypesRequested = false

// Lazily registers @types/node on first JS/TS worker request. The TS contribution import
// is what populates `languages.typescript` (Monaco's ESM build leaves it undefined otherwise).
function loadNodeTypes() {
    Promise.all([
        import("monaco-editor/esm/vs/language/typescript/monaco.contribution"),
        import("monaco-editor/esm/vs/editor/editor.api"),
        import("./monacoNodeTypes"),
    ]).then(([, {languages}, {default: nodeTypes}]) => {
        for (const path in nodeTypes) {
            languages.typescript.typescriptDefaults.addExtraLib(nodeTypes[path], `file://${path}`)
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
