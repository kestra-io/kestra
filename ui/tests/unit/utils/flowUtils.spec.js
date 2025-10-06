import {describe, it, expect} from "vitest"
import {YamlUtils as YAML_UTILS} from "@kestra-io/ui-libs";
import FlowUtils from "../../../src/utils/flowUtils";

export const flat = `
id: flat
namespace: io.kestra.tests

tasks:
  - id: 1-1
    type: io.kestra.plugin.core.log.Log
    # comment to keep
    message: 'echo "1-1"'
  - id: 1-2
    type: io.kestra.plugin.core.log.Log
    message: 'echo "1-2"'
`

export const flowable = `
id: flowable
namespace: io.kestra.tests

tasks:
  - id: nest-1
    type: io.kestra.plugin.core.flow.Parallel
    tasks:
      - id: nest-2
        type: io.kestra.plugin.core.flow.Parallel
        tasks:
        - id: nest-3
          type: io.kestra.plugin.core.flow.Parallel
          tasks:
          - id: nest-4
            type: io.kestra.plugin.core.flow.Parallel
            tasks:
              - id: 1-1
                type: io.kestra.plugin.core.log.Log
                message: 'echo "1-1"'
              - id: 1-2
                type: io.kestra.plugin.core.log.Log
                message: 'echo "1-2"'

  - id: end
    type: io.kestra.plugin.core.log.Log
    commands:
      - 'echo "end"'
`

export const plugins = `
id: flowable
namespace: io.kestra.tests

tasks:
  - id: nest-1
    type: io.kestra.core.tasks.unittest.Example
    task:
      id: 1-1
      type: io.kestra.plugin.core.log.Log
      message: "1-1"
  - id: end
    type: io.kestra.plugin.core.log.Log
    message: "end"
`

describe("FlowUtils", () => {
    it("extractTask from a flat flow", () => {
        let flow = YAML_UTILS.parse(flat);
        let findTaskById = FlowUtils.findTaskById(flow, "1-2");

        expect(findTaskById.id).toBe("1-2");
        expect(findTaskById.type).toBe("io.kestra.plugin.core.log.Log");
    })

    it("extractTask from a flowable flow", () => {
        let flow = YAML_UTILS.parse(flowable);
        let findTaskById = FlowUtils.findTaskById(flow, "1-2");

        expect(findTaskById.id).toBe("1-2");
        expect(findTaskById.type).toBe("io.kestra.plugin.core.log.Log");
    })

    it("extractTask from a flowable flow", () => {
        let flow = YAML_UTILS.parse(plugins);
        let findTaskById = FlowUtils.findTaskById(flow, "nest-1");

        expect(findTaskById.id).toBe("nest-1");
        expect(findTaskById.type).toBe("io.kestra.core.tasks.unittest.Example");
    })

    it("missing task from a flowable flow", () => {
        let flow = YAML_UTILS.parse(flowable);
        let findTaskById = FlowUtils.findTaskById(flow, "undefined");

        expect(findTaskById).toBeUndefined();
    })
})
