import {describe, it, expect} from "vitest"
import YamlUtils from "../../../src/utils/yamlUtils";
import FlowUtils from "../../../src/utils/flowUtils";
import {flat, flowable, plugins} from "./yamlUtils.spec";

describe("FlowUtils", () => {
    it("extractTask from a flat flow", () => {
        let flow = YamlUtils.parse(flat);
        let findTaskById = FlowUtils.findTaskById(flow, "1-2");

        expect(findTaskById.id).toBe("1-2");
        expect(findTaskById.type).toBe("io.kestra.plugin.core.log.Log");
    })

    it("extractTask from a flowable flow", () => {
        let flow = YamlUtils.parse(flowable);
        let findTaskById = FlowUtils.findTaskById(flow, "1-2");

        expect(findTaskById.id).toBe("1-2");
        expect(findTaskById.type).toBe("io.kestra.plugin.core.log.Log");
    })

    it("extractTask from a flowable flow", () => {
        let flow = YamlUtils.parse(plugins);
        let findTaskById = FlowUtils.findTaskById(flow, "nest-1");

        expect(findTaskById.id).toBe("nest-1");
        expect(findTaskById.type).toBe("io.kestra.core.tasks.unittest.Example");
    })

    it("missing task from a flowable flow", () => {
        let flow = YamlUtils.parse(flowable);
        let findTaskById = FlowUtils.findTaskById(flow, "undefined");

        expect(findTaskById).toBeUndefined();
    })
})
