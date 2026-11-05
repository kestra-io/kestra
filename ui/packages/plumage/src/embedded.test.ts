import {describe, expect, it} from "vitest"
import {embeddedBlocks} from "./index.ts"

const FLOW = `id: embedded
namespace: company.team
tasks:
  - id: query
    type: io.kestra.plugin.jdbc.postgresql.Query
    sql: |
      SELECT 1

      FROM dual
    fetchType: FETCH_ONE
  - id: python
    script: |
      import json
    type: io.kestra.plugin.scripts.python.Script
  - id: shell
    type: io.kestra.plugin.scripts.shell.Commands
    commands:
      - |
        echo "{{ inputs.name }}"
  - id: windows
    type: io.kestra.plugin.scripts.powershell.Commands
    commands:
      - >-
        Get-ChildItem
    inputFiles:
      main.py: |
        print(1)
  - id: webhook
    type: io.kestra.plugin.core.http.Request
    description: |
      [ERROR] lines are not JSON
    body: |
      {"text": "{{ outputs.query.row }}"}
`

describe("embeddedBlocks", () => {
    it("finds each code block and its language from the key, the task type, a file name or JSON content", () => {
        expect(embeddedBlocks(FLOW)).toEqual([
            {startLine: 6, endLine: 8, indent: 6, language: "sql"},
            {startLine: 12, endLine: 12, indent: 6, language: "python"},
            {startLine: 18, endLine: 18, indent: 8, language: "shell"},
            {startLine: 23, endLine: 23, indent: 8, language: "powershell"},
            {startLine: 26, endLine: 26, indent: 8, language: "python"},
            {startLine: 32, endLine: 32, indent: 6, language: "json"},
        ])
    })

    it("asks the resolver first, with the type of the task holding the key", () => {
        const resolve = (taskType: string | undefined, key: string) => key === "body" ? `${taskType}#${key}` : undefined

        expect(embeddedBlocks(FLOW, resolve).map(block => block.language))
            .toEqual(["sql", "python", "shell", "powershell", "python", "io.kestra.plugin.core.http.Request#body"])
    })

    it("does not take Pebble or bracketed text for JSON", () => {
        expect(embeddedBlocks("message: |\n  {{ outputs.query.row }}\nnote: |\n  [ERROR] disk full\n")).toEqual([])
    })
})
