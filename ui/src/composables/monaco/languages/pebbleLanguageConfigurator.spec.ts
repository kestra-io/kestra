import {describe, expect, it, vi} from "vitest"

globalThis.MonacoEnvironment = {
    getWorker: () => ({postMessage(){}, terminate(){}, addEventListener(){}, removeEventListener(){}}) as unknown as Worker,
}

import * as monaco from "monaco-editor/editor/editor.api.js"

vi.mock("@kestra-io/topology", () => ({flowYamlUtils: {parse: () => ({})}}))

import {PebbleAutoCompletion} from "../../../services/autoCompletionProvider"
import {
    registerPebbleAutocompletion,
    registerFunctionParametersAutoCompletion,
    registerNestedValueAutoCompletion,
    registerFilterAutoCompletion,
} from "./pebbleLanguageConfigurator"

describe.each([
    ["pebble root variable", "{{ inputs",  10, registerPebbleAutocompletion,             {rootFieldAutoCompletion:   () => Promise.resolve(["inputs"]), functionsWithDefaults: () => Promise.resolve([])}],
    ["function parameters",  "{{ secret(", 11, registerFunctionParametersAutoCompletion, {functionAutoCompletion:    () => Promise.resolve(["'value'"])}],
    ["nested value",         "{{ flow.",    9, registerNestedValueAutoCompletion,        {nestedFieldAutoCompletion: () => Promise.resolve(["id"])}],
    ["filter",               "{{ x | up",  10, registerFilterAutoCompletion,             {filterAutoCompletion:      () => Promise.resolve(["upper"])}],
])("%s autocompletion", (_label, text, column, register, ac) => {
    it("returns incomplete:true so Monaco re-invokes provider on every keystroke", async () => {
        const spy = vi.spyOn(monaco.languages, "registerCompletionItemProvider")
            .mockImplementation(() => ({dispose: () => {}}))
        const model = monaco.editor.createModel(text)
        try {
            register([], Object.assign(new PebbleAutoCompletion(), ac), ["yaml"])
            const provider = spy.mock.calls[0][1]
            const result = await provider.provideCompletionItems(
                model,
                new monaco.Position(1, column),
                {triggerKind: monaco.languages.CompletionTriggerKind.Invoke},
                new monaco.CancellationTokenSource().token,
            )
            expect(result?.incomplete).toBe(true)
        } finally {
            model.dispose()
            spy.mockRestore()
        }
    })
})
