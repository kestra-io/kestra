import {describe, expect, it} from "vitest";
import {FIRST_FLOW_GUIDE_STEPS} from "../../../src/components/onboarding/guides/firstFlowGuide";

const findStep = (id: string) => {
    const step = FIRST_FLOW_GUIDE_STEPS.find((candidate) => candidate.id === id);
    if (!step) {
        throw new Error(`Missing onboarding step: ${id}`);
    }
    return step;
};

describe("firstFlowGuide validations", () => {
    it("requires id for add_id step", () => {
        const result = findStep("add_id").validate({
            flowYaml: "namespace: company.team",
            routeName: "flows/create",
            saveCount: 0,
            executionCount: 0,
        });

        expect(result.ok).toBe(false);
        expect(result.message).toBe("onboarding.validation.add_id");
    });

    it("accepts a valid first python task with inputs usage", () => {
        const result = findStep("add_log_task").validate({
            flowYaml: `id: my_flow
namespace: company.team
inputs:
  - id: name
    type: STRING
tasks:
  - id: greet
    type: io.kestra.plugin.scripts.python.Script
    script: |
      print("Hello {{ inputs.name }}")`,
            routeName: "flows/create",
            saveCount: 0,
            executionCount: 0,
        });

        expect(result.ok).toBe(true);
    });

    it("requires a real save event for save_flow step", () => {
        const step = findStep("save_flow");

        const beforeSave = step.validate({
            flowYaml: "",
            routeName: "flows/update",
            saveCount: 0,
            executionCount: 0,
        });
        const afterSave = step.validate({
            flowYaml: "",
            routeName: "flows/update",
            saveCount: 1,
            executionCount: 0,
        });

        expect(beforeSave.ok).toBe(false);
        expect(afterSave.ok).toBe(true);
    });
});
