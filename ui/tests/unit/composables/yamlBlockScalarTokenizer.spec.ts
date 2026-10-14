import {describe, expect, it} from "vitest"

globalThis.MonacoEnvironment = {
    getWorker: () => ({postMessage(){}, terminate(){}, addEventListener(){}, removeEventListener(){}}) as unknown as Worker,
}

import * as monaco from "monaco-editor/editor/editor.api.js"
import "monaco-editor/languages/definitions/yaml/register.js"
import {
    registerIndentedBlockScalarTokenizer,
    withIndentedBlockScalarContent,
} from "../../../src/composables/monaco/languages/yamlBlockScalarTokenizer"

const FLOW = [
    "tasks:",
    "  - id: same_indent",
    "    inputFiles:",
    "      other.yml: |",
    "        hosts: localhost",
    "        gather_facts: false",
    "  - id: deeper_indent",
    "    inputFiles:",
    "      playbook.yml: |",
    "        - name: Output JSON to logs",
    "          hosts: localhost",
    "          gather_facts: false",
    "  - id: python",
    "    script: |",
    "      for i in range(3):",
    "          print(i)",
    "      config: 1",
    "    type: io.kestra.plugin.scripts.python.Script",
].join("\n")

/** Token types of a line, ignoring whitespace-only tokens. */
function typesOf(lines: monaco.Token[][], lineIndex: number): string[] {
    const text = FLOW.split("\n")[lineIndex]
    const line = lines[lineIndex]
    return line
        .map((token, i) => ({type: token.type, text: text.slice(token.offset, line[i + 1]?.offset ?? text.length)}))
        .filter(token => token.text.trim() !== "")
        .map(token => token.type)
}

async function tokenizeFlow() {
    await registerIndentedBlockScalarTokenizer("yaml")
    return monaco.editor.tokenize(FLOW, "yaml")
}

describe("YAML block scalar tokenizer", () => {
    it("keeps lines indented deeper than the first one as plain string", async () => {
        const lines = await tokenizeFlow()

        for (const index of [4, 5, 9, 10, 11, 14, 15, 16]) {
            expect(typesOf(lines, index), `line ${index}`).toEqual(["string.yaml"])
        }
    })

    it("ends the block at the first line indented less than its content", async () => {
        const lines = await tokenizeFlow()

        expect(typesOf(lines, 12)).toContain("type.yaml")
        expect(typesOf(lines, 17)[0]).toBe("type.yaml")
    })

    it("leaves a language without a block scalar rule untouched", () => {
        const language = {tokenizer: {root: []}}

        expect(withIndentedBlockScalarContent(language)).toBe(language)
    })
})
