import {expect, test} from "@playwright/test";
import {FlowsApi} from "../api/flows.api";
import {NoCodePage} from "../pages/nocode.page";
import {shared} from "../fixtures/shared";

/**
 * E2E tests for the no-code editor — ForEach tasks.
 *
 * Covers:
 * - Creating a ForEach task from scratch via the no-code editor
 * - Editing the ForEach task (changing values)
 * - Adding inner tasks via the no-code editor and verifying they appear in YAML
 * - Removing inner tasks via the no-code editor and verifying the YAML update
 * - Adding an inner task via the code editor (YAML) and verifying it appears in no-code
 * - Removing an inner task via the code editor and verifying it disappears in no-code
 *
 * After each action the YAML is verified and the flow is saved to confirm
 * no validation errors are thrown.
 */

test.describe("No-code editor — ForEach", () => {
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

    test("should create a ForEach task using the no-code editor", async ({page, request, baseURL}) => {
        flowId = await flowsApi.generateFlowViaApi("nocode-empty.yaml", "nocode-empty");
        await noCodePage.gotoFlowEdit(shared.namespace, flowId);
        await noCodePage.openNoCodePanel();

        await test.step("add a ForEach task", async () => {
            await noCodePage.addEntry(
                "tasks",
                "io.kestra.plugin.core.flow.ForEach",
                "my_foreach",
            );
        });

        await test.step("fill the values field", async () => {
            await noCodePage.fillField("values", '["item1", "item2"]');
        });

        await test.step("verify YAML contains the ForEach task", async () => {
            await noCodePage.expectYamlContains("io.kestra.plugin.core.flow.ForEach");
            await noCodePage.expectYamlContains("my_foreach");
        });

        await test.step("save and confirm no error", async () => {
            await noCodePage.saveFlow();
        });
    });

    test("should edit a ForEach task in the no-code editor", async ({page, request, baseURL}) => {
        flowId = await flowsApi.generateFlowViaApi("nocode-foreach.yaml", "nocode-foreach");
        await noCodePage.gotoFlowEdit(shared.namespace, flowId);
        await noCodePage.openNoCodePanel();

        await test.step("click the ForEach task element to open its edit tab", async () => {
            await noCodePage.clickElement("each");
        });

        await test.step("update the values field", async () => {
            await noCodePage.fillField("values", '["updated1", "updated2", "updated3"]');
        });

        await test.step("verify YAML reflects the updated values", async () => {
            await noCodePage.expectYamlContains("updated1");
            await noCodePage.expectYamlContains("updated2");
            await noCodePage.expectYamlContains("updated3");
        });

        await test.step("save and confirm no error", async () => {
            await noCodePage.saveFlow();
        });
    });

    test("should add an inner task to ForEach via the no-code editor", async ({page, request, baseURL}) => {
        flowId = await flowsApi.generateFlowViaApi("nocode-foreach.yaml", "nocode-foreach");
        await noCodePage.gotoFlowEdit(shared.namespace, flowId);
        await noCodePage.openNoCodePanel();

        await test.step("click the ForEach task element to open its edit tab", async () => {
            await noCodePage.clickElement("each");
        });

        await test.step("add a new inner Log task to the ForEach tasks list", async () => {
            // The ForEach edit tab shows the tasks sub-list for inner tasks
            await noCodePage.addEntry("tasks", "io.kestra.plugin.core.log.Log", "new_inner_log");
        });

        await test.step("verify YAML shows the new inner task nested under ForEach", async () => {
            await noCodePage.expectYamlContains("new_inner_log");
            await noCodePage.expectYamlContains("io.kestra.plugin.core.log.Log");
        });

        await test.step("save and confirm no error", async () => {
            await noCodePage.saveFlow();
        });
    });

    test("should remove an inner task from ForEach via the no-code editor", async ({page, request, baseURL}) => {
        flowId = await flowsApi.generateFlowViaApi("nocode-foreach.yaml", "nocode-foreach");
        await noCodePage.gotoFlowEdit(shared.namespace, flowId);
        await noCodePage.openNoCodePanel();

        await test.step("verify the inner task is present in YAML", async () => {
            await noCodePage.expectYamlContains("inner_log");
        });

        await test.step("click the ForEach task element to open its edit tab", async () => {
            await noCodePage.clickElement("each");
        });

        await test.step("delete the inner_log task element", async () => {
            await noCodePage.deleteElement("inner_log");
        });

        await test.step("verify the inner task is removed from YAML", async () => {
            await noCodePage.expectYamlNotContains("inner_log");
        });

        await test.step("save and confirm no error", async () => {
            await noCodePage.saveFlow();
        });
    });

    test("should reflect changes made to ForEach inner tasks via the code editor", async ({page, request, baseURL}) => {
        flowId = await flowsApi.generateFlowViaApi("nocode-foreach.yaml", "nocode-foreach");
        await noCodePage.gotoFlowEdit(shared.namespace, flowId);
        await noCodePage.openNoCodePanel();

        await test.step("add a new inner task via the code editor", async () => {
            const originalYaml = await noCodePage.getFlowYaml();
            // Insert a new inner task after the existing one in the YAML
            const updatedYaml = originalYaml.replace(
                "      - id: inner_log",
                "      - id: code_added_task\n        type: io.kestra.plugin.core.log.Log\n        message: added via code\n      - id: inner_log",
            );
            await noCodePage.setFlowYaml(updatedYaml);
        });

        await test.step("verify the new task appears in the no-code element list", async () => {
            // Reopen no-code panel to refresh the view
            await noCodePage.openNoCodePanel();
            await noCodePage.clickElement("each");
            await expect(
                noCodePage.page.locator(".element").filter({hasText: "code_added_task"}),
            ).toBeVisible();
        });

        await test.step("verify YAML contains the code-added task", async () => {
            await noCodePage.expectYamlContains("code_added_task");
        });

        await test.step("save and confirm no error", async () => {
            await noCodePage.saveFlow();
        });
    });

    test("should reflect removal of ForEach inner tasks made via the code editor", async ({page, request, baseURL}) => {
        flowId = await flowsApi.generateFlowViaApi("nocode-foreach.yaml", "nocode-foreach");
        await noCodePage.gotoFlowEdit(shared.namespace, flowId);
        await noCodePage.openNoCodePanel();

        await test.step("remove the inner task via the code editor", async () => {
            const originalYaml = await noCodePage.getFlowYaml();
            // Remove the inner_log task from the YAML
            const updatedYaml = originalYaml
                .split("\n")
                .filter((line) => !line.includes("inner_log") && !line.includes('message: "{{ taskrun.value }}"'))
                .join("\n");
            await noCodePage.setFlowYaml(updatedYaml);
        });

        await test.step("verify the task no longer appears in the no-code element list", async () => {
            await noCodePage.openNoCodePanel();
            await noCodePage.clickElement("each");
            await expect(
                noCodePage.page.locator(".element").filter({hasText: "inner_log"}),
            ).not.toBeVisible();
        });

        await test.step("verify YAML no longer contains the removed task", async () => {
            await noCodePage.expectYamlNotContains("inner_log");
        });

        await test.step("save and confirm no error", async () => {
            await noCodePage.saveFlow();
        });
    });
});
