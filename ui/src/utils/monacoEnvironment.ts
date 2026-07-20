import EditorWorker from "monaco-editor/esm/vs/editor/editor.worker?worker"
import JsonWorker from "monaco-editor/esm/vs/language/json/json.worker?worker"
import TypeScriptWorker from "monaco-editor/esm/vs/language/typescript/ts.worker?worker"
import YamlWorker from "../components/inputs/yaml.worker.js?worker"

const nodeTypesRaw = import.meta.glob("/node_modules/@types/node/**/*.d.ts", {query: "?raw", import: "default"}) as Record<string, () => Promise<string>>

let nodeTypesRequested = false

// Registers @types/node into Monaco's TS service on first JS/TS worker request — both
// the types and the TS language contribution are multi-MB payloads that must not load
// at boot. Importing the contribution is what populates `languages.typescript` (Monaco's
// ESM build leaves it undefined otherwise).
function loadNodeTypes() {
    Promise.all([
        import("monaco-editor/esm/vs/language/typescript/monaco.contribution"),
        import("monaco-editor/esm/vs/editor/editor.api"),
    ]).then(([, {languages}]) => {
        for (const path in nodeTypesRaw) {
            nodeTypesRaw[path]().then((content) => {
                languages.typescript.typescriptDefaults.addExtraLib(content, `file://${path}`)
            })
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
