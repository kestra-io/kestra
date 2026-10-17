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
    NO_SUGGESTIONS,
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
        // Line: "variable" -> length 8. Start of word: column 1. End of word: column 9
        const model = monaco.editor.createModel("variable")
        try {
            expect(endOfWordColumn(new monaco.Position(1, 1), model)).toBe(9)
        } finally {
            model.dispose()
        }
    })

    it("resolves to the end of the word when cursor is mid-word", () => {
        // Line: "variable" -> cursor at 'r' (column 3). End of word is column 9.
        const model = monaco.editor.createModel("variable")
        try {
            expect(endOfWordColumn(new monaco.Position(1, 3), model)).toBe(9)
        } finally {
            model.dispose()
        }
    })

    it("returns the cursor column itself when cursor is on whitespace", () => {
        // Line: "   " -> cursor at column 2 (whitespace). Matches empty word -> returns column 2.
        const model = monaco.editor.createModel("   ")
        try {
            expect(endOfWordColumn(new monaco.Position(1, 2), model)).toBe(2)
        } finally {
            model.dispose()
        }
    })

    it("stops at punctuation / separators that terminate the word", () => {
        // Line: "inputs.name" -> cursor at 1. Separator is dot or space or colon.
        // Before separator matches "inputs" (length 6), so end column is 1 + 6 = 7.
        const model = monaco.editor.createModel("inputs: test")
        try {
            expect(endOfWordColumn(new monaco.Position(1, 1), model)).toBe(7)
        } finally {
            model.dispose()
        }
    })
})

describe("NO_SUGGESTIONS", () => {
    it("matches the empty-suggestion shape Monaco expects", () => {
        expect(NO_SUGGESTIONS).toEqual({suggestions: []})
        expect(NO_SUGGESTIONS.suggestions).toBeDefined()
        expect(Array.isArray(NO_SUGGESTIONS.suggestions)).toBe(true)
        expect(NO_SUGGESTIONS.suggestions).toHaveLength(0)
    })
})

