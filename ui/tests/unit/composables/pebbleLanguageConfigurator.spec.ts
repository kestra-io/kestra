import {describe, expect, it, vi} from "vitest";

(globalThis as any).MonacoEnvironment = {
    getWorker: () => ({postMessage(){}, terminate(){}, addEventListener(){}, removeEventListener(){}}),
}

import * as monaco from "monaco-editor/esm/vs/editor/editor.api"

vi.mock("@kestra-io/topology", () => ({flowYamlUtils: {parse: () => ({})}}))

import {
    registerPebbleAutocompletion,
    registerFunctionParametersAutoCompletion,
    registerNestedValueAutoCompletion,
    registerFilterAutoCompletion,
} from "../../../src/composables/monaco/languages/pebbleLanguageConfigurator"

describe.each([
    ["pebble root variable", "{{ inputs",  10, registerPebbleAutocompletion,             {rootFieldAutoCompletion:   () => Promise.resolve(["inputs"])}],
    ["function parameters",  "{{ secret(", 11, registerFunctionParametersAutoCompletion, {functionAutoCompletion:    () => Promise.resolve(["'value'"])}],
    ["nested value",         "{{ flow.",    9, registerNestedValueAutoCompletion,        {nestedFieldAutoCompletion: () => Promise.resolve(["id"])}],
    ["filter",               "{{ x | up",  10, registerFilterAutoCompletion,             {filterAutoCompletion:      () => Promise.resolve(["upper"])}],
])("%s autocompletion", (_label, text, column, register, ac) => {
    it("returns incomplete:true so Monaco re-invokes provider on every keystroke", async () => {
        const spy = vi.spyOn(monaco.languages, "registerCompletionItemProvider")
            .mockImplementation((() => ({dispose: () => {}})) as any)
        const model = monaco.editor.createModel(text)
        try {
            register([], ac as any, ["yaml"])
            const provider = spy.mock.calls[0][1] as any
            const result = await provider.provideCompletionItems(model, {lineNumber: 1, column} as any)
            expect(result.incomplete).toBe(true)
        } finally {
            model.dispose()
            spy.mockRestore()
        }
    })
})
