import {describe, it, expect} from "vitest"
import YamlUtils from "../../../src/utils/yamlUtils";

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

const replace = `
id: replaced
type: io.kestra.plugin.core.log.Log
# comment to add
message: "replaced"
`

const extractMapsSample = `
firstMap:
  populatedField:
  presentField:
  extraField: "firstMap"
secondMap:
  populatedField: "populated"
  presentField:
  extraField: "secondMap"
thirdMap:
  populatedField: "populated"
  extraField: "thirdMap"
`

describe("YamlUtils", () => {
    it("extractMaps with field conditions", () => {
        const extractMaps = YamlUtils.extractMaps(extractMapsSample, {
            populatedField: {
                populated: true
            },
            presentField: {
                present: true
            }
        });

        expect(extractMaps.length).toBe(1);
        const map = extractMaps[0].map;
        expect(map.populatedField).toBe("populated");
        expect(map.presentField).toBe(undefined);
        expect(map.extraField).toBe("secondMap");
        expect(extractMaps[0].range).toStrictEqual([83,153,153]);
    })

    it("extractTask from a flat flow", () => {
        let doc = YamlUtils.extractTask(flat, "1-1", "tasks");

        expect(doc.toString()).toContain("\"1-1\"");
        expect(doc.toString()).toContain("# comment to keep");
    })

    it("extractTask from a flowable flow", () => {
        let doc = YamlUtils.extractTask(flowable, "1-2", "tasks");

        expect(doc.toString()).toContain("\"1-2\"");
    })

    it("extractTask from a plugin flow", () => {
        let doc = YamlUtils.extractTask(plugins, "1-1", "tasks");

        expect(doc.toString()).toContain("\"1-1\"");
    })

    it("extractTask undefined from a flowable flow", () => {
        let doc = YamlUtils.extractTask(flowable, "X-X", "tasks");

        expect(doc).toBe(undefined);
    })

    it("replace from a flat flow", () => {
        let doc = YamlUtils.replaceTaskInDocument(flat, "1-1", replace, "tasks");

        expect(doc.toString()).toContain("\"replaced\"");
        expect(doc.toString()).toContain("echo \"1-2\"");
        expect(doc.toString()).toContain("# comment to add");
        expect(doc.toString()).not.toContain("# comment to keep");
    })

    it("replace from a flowable flow", () => {
        let doc = YamlUtils.replaceTaskInDocument(flowable, "1-2", replace, "tasks");

        expect(doc.toString()).toContain("\"replaced\"");
        expect(doc.toString()).toContain("echo \"1-1\"");
        expect(doc.toString()).toContain("# comment to add");
    })

    it("replace from a plugin flow", () => {
        let doc = YamlUtils.replaceTaskInDocument(plugins, "1-1", replace, "tasks");

        expect(doc.toString()).toContain("\"replaced\"");
        expect(doc.toString()).toContain("unittest.Example");
        expect(doc.toString()).toContain("# comment to add");
    })
})
