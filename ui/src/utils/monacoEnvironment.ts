import EditorWorker from "monaco-editor/esm/vs/editor/editor.worker?worker"
import JsonWorker from "monaco-editor/esm/vs/language/json/json.worker?worker"
import TypeScriptWorker from "monaco-editor/esm/vs/language/typescript/ts.worker?worker"
import YamlWorker from "../components/inputs/yaml.worker.js?worker"

let nodeTypesRequested = false

// Registers @types/node into Monaco's TypeScript language service the first
// time a JS/TS worker is requested. Both Monaco and the raw typings are
// multi-megabyte payloads that must not load at boot; extra libs added after
// worker creation still propagate to open models. The retry loop waits for
// the typescript language contribution to finish registering.
function loadNodeTypes(tries = 0) {
    Promise.all([
        import("monaco-editor/esm/vs/editor/editor.api"),
        import("./nodeTypes"),
    ]).then(([{languages}, {default: nodeTypesRaw}]) => {
        if (languages.typescript) {
            for (const path in nodeTypesRaw) {
                languages.typescript.typescriptDefaults.addExtraLib(nodeTypesRaw[path], `file://${path}`)
            }
        } else if (tries <= 15) {
            setTimeout(() => loadNodeTypes(tries + 1), (tries + 1) * 100)
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
