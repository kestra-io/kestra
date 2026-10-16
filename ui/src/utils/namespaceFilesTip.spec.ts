import {describe, it, expect} from "vitest"
import * as YAML_UTILS from "@kestra-io/topology/flow-yaml-utils"
import {hasInlinePythonScript} from "../../../src/utils/namespaceFilesTip"

const parse = (yaml: string) => YAML_UTILS.parse(yaml)

describe("hasInlinePythonScript", () => {
    it("detects a Python Script task with an inline script", () => {
        expect(hasInlinePythonScript(parse(`
id: inline
namespace: company.team
tasks:
  - id: python
    type: io.kestra.plugin.scripts.python.Script
    script: |
      print("hello")
`))).toBe(true)
    })

    it("detects an inline Python script nested in a flowable task", () => {
        expect(hasInlinePythonScript(parse(`
id: nested
namespace: company.team
tasks:
  - id: parallel
    type: io.kestra.plugin.core.flow.Parallel
    tasks:
      - id: python
        type: io.kestra.plugin.scripts.python.Script
        script: print("hello")
`))).toBe(true)
    })

    it("detects an inline Python script in errors", () => {
        expect(hasInlinePythonScript(parse(`
id: errors
namespace: company.team
tasks:
  - id: log
    type: io.kestra.plugin.core.log.Log
    message: hello
errors:
  - id: python
    type: io.kestra.plugin.scripts.python.Script
    script: print("failed")
`))).toBe(true)
    })

    it("ignores a Python Script task that already uses Namespace Files", () => {
        expect(hasInlinePythonScript(parse(`
id: ns-files
namespace: company.team
tasks:
  - id: python
    type: io.kestra.plugin.scripts.python.Script
    namespaceFiles:
      enabled: true
    script: print("hello")
`))).toBe(false)
    })

    it("ignores an empty script", () => {
        expect(hasInlinePythonScript(parse(`
id: empty
namespace: company.team
tasks:
  - id: python
    type: io.kestra.plugin.scripts.python.Script
    script: "  "
`))).toBe(false)
    })

    it("ignores flows without a Python Script task", () => {
        expect(hasInlinePythonScript(parse(`
id: commands
namespace: company.team
tasks:
  - id: python
    type: io.kestra.plugin.scripts.python.Commands
    commands:
      - python main.py
  - id: log
    type: io.kestra.plugin.core.log.Log
    message: hello
`))).toBe(false)
    })

    it("handles missing or invalid input", () => {
        expect(hasInlinePythonScript(undefined)).toBe(false)
        expect(hasInlinePythonScript(null)).toBe(false)
        expect(hasInlinePythonScript("not a flow")).toBe(false)
    })
})
