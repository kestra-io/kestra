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
    endOfWordColumn,
} from "./pebbleLanguageConfigurator"

describe.each([
    ["pebble root variable", "{{ inputs",  10, registerPebbleAutocompletion,             {rootFieldAutoCompletion:   () => Promise.resolve(["inputs"]), functionsWithDefaults: () => Promise.resolve([])}],
    ["statement root variable", "{% inputs", 10, registerPebbleAutocompletion,           {rootFieldAutoCompletion:   () => Promise.resolve(["inputs"]), functionsWithDefaults: () => Promise.resolve([])}],
    ["empty statement block", "{% ", 4, registerPebbleAutocompletion,                    {rootFieldAutoCompletion:   () => Promise.resolve(["inputs"]), functionsWithDefaults: () => Promise.resolve([])}],
    ["function parameters",  "{{ secret(", 11, registerFunctionParametersAutoCompletion, {functionAutoCompletion:    () => Promise.resolve(["'value'"])}],
    ["nested value",         "{{ flow.",    9, registerNestedValueAutoCompletion,        {nestedFieldAutoCompletion: () => Promise.resolve(["id"])}],
    ["statement nested value", "{% inputs.", 11, registerNestedValueAutoCompletion,      {nestedFieldAutoCompletion: () => Promise.resolve(["cond"])}],
    ["statement if nested value", "{% if inputs.", 14, registerNestedValueAutoCompletion, {nestedFieldAutoCompletion: () => Promise.resolve(["cond"])}],
    ["filter",               "{{ x | up",  10, registerFilterAutoCompletion,             {filterAutoCompletion:      () => Promise.resolve(["upper"])}],
    ["statement filter",     "{% x | up",  10, registerFilterAutoCompletion,             {filterAutoCompletion:      () => Promise.resolve(["upper"])}],
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

describe("endOfWordColumn", () => {
    it("returns the column at the end of the word under the cursor", () => {
        const model = monaco.editor.createModel("{{ inputs")
        try {
            expect(endOfWordColumn(new monaco.Position(1, 4), model)).toBe(10)
        } finally {
            model.dispose()
        }
    })

    it("a cursor mid-word still resolves to the word's end", () => {
        const model = monaco.editor.createModel("{{ inputs")
        try {
            expect(endOfWordColumn(new monaco.Position(1, 7), model)).toBe(10)
        } finally {
            model.dispose()
        }
    })

    it("a cursor on whitespace returns the cursor column itself", () => {
        const model = monaco.editor.createModel("foo bar")
        try {
            expect(endOfWordColumn(new monaco.Position(1, 4), model)).toBe(4)
        } finally {
            model.dispose()
        }
    })

    it("punctuation terminates the word", () => {
        const model = monaco.editor.createModel("{{ inputs}}")
        try {
            expect(endOfWordColumn(new monaco.Position(1, 4), model)).toBe(10)
        } finally {
            model.dispose()
        }
    })
})

describe.each([
    ["pebble root variable", "plain text", 11, registerPebbleAutocompletion],
    ["function parameters", "plain text", 11, registerFunctionParametersAutoCompletion],
    ["nested value", "plain text.", 12, registerNestedValueAutoCompletion],
    ["filter", "plain | up", 11, registerFilterAutoCompletion],
    ["filter without a pipe", "{{ plain", 9, registerFilterAutoCompletion],
])("%s autocompletion outside a pebble expression", (_label, text, column, register) => {
    it("returns an empty suggestion list rather than undefined", async () => {
        const spy = vi.spyOn(monaco.languages, "registerCompletionItemProvider")
            .mockImplementation(() => ({dispose: () => {}}))
        const model = monaco.editor.createModel(text)
        try {
            register([], new PebbleAutoCompletion(), ["yaml"])
            const provider = spy.mock.calls[0][1]
            const result = await provider.provideCompletionItems(
                model,
                new monaco.Position(1, column),
                {triggerKind: monaco.languages.CompletionTriggerKind.Invoke},
                new monaco.CancellationTokenSource().token,
            )
            expect(result).toEqual({suggestions: []})
        } finally {
            model.dispose()
            spy.mockRestore()
        }
    })
})
