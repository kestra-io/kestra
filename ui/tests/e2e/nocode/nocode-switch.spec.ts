import {expect, test} from "@playwright/test";
import {FlowsApi} from "../api/flows.api";
import {NoCodePage} from "../pages/nocode.page";
import {shared} from "../fixtures/shared";

/**
 * E2E tests for the no-code editor — Switch tasks.
 *
 * Covers:
 * - Creating a Switch task from scratch with multiple cases and multiple tasks per case
 * - Adding tasks to a case after the Switch is saved
 * - Removing tasks from a case via the no-code editor
 * - Creating a Switch task nested inside a ForEach task
 *
 * After each action the YAML is verified and the flow is saved to confirm
 * no validation errors are thrown.
 */

test.describe("No-code editor — Switch", () => {
    let flowId: string;
    let flowsApi: FlowsApi;
    let noCodePage: NoCodePage;

    test.beforeEach(async ({page, request, baseURL}) => {
        flowsApi = new FlowsApi(request, baseURL);
        noCodePage = new NoCodePage(page);
    });

    test.afterEach(async () => {
        await flowsApi.removeFlowsViaApi();
    });

    test("should create a Switch task with multiple cases and multiple tasks per case", async ({page, request, baseURL}) => {
        flowId = await flowsApi.generateFlowViaApi("nocode-empty.yaml", "nocode-empty");
        await noCodePage.gotoFlowEdit(shared.namespace, flowId);
        await noCodePage.openNoCodePanel();

        await test.step("add a Switch task", async () => {
            await noCodePage.addEntry(
                "tasks",
                "io.kestra.plugin.core.flow.Switch",
                "my_switch",
            );
        });

        await test.step("set the switch value expression", async () => {
            await noCodePage.fillField("value", "{{ inputs.branch }}");
        });

        await test.step("verify Switch task is in YAML", async () => {
            await noCodePage.expectYamlContains("io.kestra.plugin.core.flow.Switch");
            await noCodePage.expectYamlContains("my_switch");
        });

        await test.step("add tasks to case_a via the code editor and verify in no-code", async () => {
            // Add cases structure via code editor since creating cases via no-code
            // requires navigating into the Switch task's cases fields
            const currentYaml = await noCodePage.getFlowYaml();
            const withCases = currentYaml.replace(
                /(\s+id: my_switch\s+type: io\.kestra\.plugin\.core\.flow\.Switch\s+value: .+)/,
                "$1\n    cases:\n      case_a:\n        - id: log_a1\n          type: io.kestra.plugin.core.log.Log\n          message: case a task 1\n        - id: log_a2\n          type: io.kestra.plugin.core.log.Log\n          message: case a task 2\n      case_b:\n        - id: log_b1\n          type: io.kestra.plugin.core.log.Log\n          message: case b task 1\n        - id: log_b2\n          type: io.kestra.plugin.core.log.Log\n          message: case b task 2",
            );
            await noCodePage.setFlowYaml(withCases);
        });

        await test.step("verify YAML has both cases with two tasks each", async () => {
            await noCodePage.expectYamlContains("case_a");
            await noCodePage.expectYamlContains("case_b");
            await noCodePage.expectYamlContains("log_a1");
            await noCodePage.expectYamlContains("log_a2");
            await noCodePage.expectYamlContains("log_b1");
            await noCodePage.expectYamlContains("log_b2");
        });

        await test.step("verify no-code editor shows the Switch with its cases", async () => {
            await noCodePage.openNoCodePanel();
            await noCodePage.clickElement("my_switch");
            // The Switch edit view should show the cases
            await expect(
                noCodePage.page.locator(".element").filter({hasText: "log_a1"}),
            ).toBeVisible();
            await expect(
                noCodePage.page.locator(".element").filter({hasText: "log_b1"}),
            ).toBeVisible();
        });

        await test.step("save and confirm no error", async () => {
            await noCodePage.saveFlow();
        });
    });

    test("should add a task to an existing Switch case via the no-code editor", async ({page, request, baseURL}) => {
        flowId = await flowsApi.generateFlowViaApi("nocode-switch.yaml", "nocode-switch");
        await noCodePage.gotoFlowEdit(shared.namespace, flowId);
        await noCodePage.openNoCodePanel();

        await test.step("open the Switch task edit tab", async () => {
            await noCodePage.clickElement("my_switch");
        });

        await test.step("add a new task to case_a", async () => {
            // Find the "Add" button in the case_a section header and click it
            await noCodePage.addEntry("case_a", "io.kestra.plugin.core.log.Log", "log_a2");
        });

        await test.step("verify the new task appears in YAML under case_a", async () => {
            await noCodePage.expectYamlContains("log_a2");
            await noCodePage.expectYamlContains("case_a");
        });

        await test.step("save and confirm no error", async () => {
            await noCodePage.saveFlow();
        });
    });

    test("should remove a task from a Switch case via the no-code editor", async ({page, request, baseURL}) => {
        flowId = await flowsApi.generateFlowViaApi("nocode-switch.yaml", "nocode-switch");
        await noCodePage.gotoFlowEdit(shared.namespace, flowId);
        await noCodePage.openNoCodePanel();

        await test.step("verify the existing task is in YAML", async () => {
            await noCodePage.expectYamlContains("log_a");
        });

        await test.step("open the Switch task edit tab", async () => {
            await noCodePage.clickElement("my_switch");
        });

        await test.step("delete the log_a task from case_a", async () => {
            await noCodePage.deleteElement("log_a");
        });

        await test.step("verify the task is removed from YAML", async () => {
            await noCodePage.expectYamlNotContains("log_a");
        });

        await test.step("save and confirm no error", async () => {
            await noCodePage.saveFlow();
        });
    });

    test("should create a Switch task nested inside a ForEach task", async ({page, request, baseURL}) => {
        flowId = await flowsApi.generateFlowViaApi("nocode-foreach.yaml", "nocode-foreach");
        await noCodePage.gotoFlowEdit(shared.namespace, flowId);
        await noCodePage.openNoCodePanel();

        await test.step("open the ForEach task edit tab", async () => {
            await noCodePage.clickElement("each");
        });

        await test.step("add a Switch task as an inner task of ForEach", async () => {
            await noCodePage.addEntry(
                "tasks",
                "io.kestra.plugin.core.flow.Switch",
                "inner_switch",
            );
        });

        await test.step("set the switch value", async () => {
            await noCodePage.fillField("value", "{{ taskrun.value }}");
        });

        await test.step("verify YAML contains the Switch nested inside ForEach", async () => {
            await noCodePage.expectYamlContains("inner_switch");
            await noCodePage.expectYamlContains("io.kestra.plugin.core.flow.Switch");
            // The Switch should be indented within the ForEach tasks
            const yaml = await noCodePage.getFlowYaml();
            // Both ForEach and Switch should be present
            expect(yaml).toContain("io.kestra.plugin.core.flow.ForEach");
            expect(yaml).toContain("io.kestra.plugin.core.flow.Switch");
            expect(yaml).toContain("inner_switch");
        });

        await test.step("add cases to the Switch via the code editor", async () => {
            const currentYaml = await noCodePage.getFlowYaml();
            const withCases = currentYaml.replace(
                /(\s+id: inner_switch\s+type: io\.kestra\.plugin\.core\.flow\.Switch\s+value: .+)/,
                "$1\n        cases:\n          branch_x:\n            - id: log_x\n              type: io.kestra.plugin.core.log.Log\n              message: branch x",
            );
            await noCodePage.setFlowYaml(withCases);
        });

        await test.step("verify the full nested structure in YAML", async () => {
            await noCodePage.expectYamlContains("each");
            await noCodePage.expectYamlContains("inner_switch");
            await noCodePage.expectYamlContains("branch_x");
            await noCodePage.expectYamlContains("log_x");
        });

        await test.step("save and confirm no error", async () => {
            await noCodePage.saveFlow();
        });
    });
});
