import {test} from "@playwright/test";
import {FlowsApi} from "../api/flows.api";
import {NoCodePage} from "../pages/nocode.page";
import {shared} from "../fixtures/shared";
// import {v4 as uuidv4} from "uuid";

/**
 * E2E tests for the no-code editor — simple tasks.
 *
 * Covers:
 * - Creating a Log task via the no-code editor
 * - Editing an existing Log task via the no-code editor
 * - Creating a trigger via the no-code editor
 * - Creating a pluginDefault via the no-code editor
 * - Cross-flow: opening a second flow and editing it via the no-code editor
 *
 * After each action the YAML is verified and the flow is saved to confirm
 * no validation errors are thrown.
 */

test.describe("No-code editor — Simple tasks (Log)", () => {
    let flowId: string;
    let flowsApi: FlowsApi;
    let noCodePage: NoCodePage;
    // let testUUID = "";

    test.beforeEach(async ({page, request, baseURL}) => {
        flowsApi = new FlowsApi(request, baseURL);

        await page.goto("/ui");

        // Each test starts with a blank flow so it can freely create what it needs.
        noCodePage = new NoCodePage(page);
        
        flowId = await flowsApi.generateFlowViaApi("nocode-simple.yaml", "nocode-simple");
        await noCodePage.gotoFlowEdit(shared.namespace, flowId);
        await noCodePage.openNoCodePanel();
        // testUUID = uuidv4().replace(/-/g, "_");
    });

    test.afterEach(async () => {
        await flowsApi.removeFlowsViaApi();
    });

    test("should create a Log task and verify the YAML is updated", async () => {
        await test.step("add a Log task in the tasks section", async () => {
            await noCodePage.addEntry("tasks", "io.kestra.plugin.core.log.Log", "my_log");
        });

        await test.step("verify YAML contains the new task", async () => {
            await noCodePage.expectYamlContains("io.kestra.plugin.core.log.Log");
            await noCodePage.expectYamlContains("my_log");
        });

        await test.step("save and confirm no error", async () => {
            await noCodePage.saveFlow();
        });
    });

    test("should edit an existing Log task via the no-code editor", async () => {
        await test.step("create a Log task first", async () => {
            await noCodePage.addEntry("tasks", "io.kestra.plugin.core.log.Log", "my_log");
        });

        await test.step("click the task element to open its edit tab", async () => {
            // Navigate back to the main no-code view (the parent tasks list)
            await noCodePage.page.getByRole("button", {name: "No-code"}).click();
            await noCodePage.clickElement("my_log");
        });

        await test.step("update the message field", async () => {
            await noCodePage.fillField("message", "Updated message from no-code editor");
        });

        await test.step("verify YAML reflects the change", async () => {
            await noCodePage.expectYamlContains("Updated message from no-code editor");
        });

        await test.step("save and confirm no error", async () => {
            await noCodePage.saveFlow();
        });
    });

    test("should create a trigger via the no-code editor", async () => {
        await test.step("add a Schedule trigger in the triggers section", async () => {
            await noCodePage.addEntry(
                "triggers",
                "io.kestra.plugin.core.trigger.Schedule",
                "my_schedule",
            );
        });

        await test.step("fill the cron expression", async () => {
            await noCodePage.fillField("cron", "0 * * * *");
        });

        await test.step("verify YAML contains the trigger", async () => {
            await noCodePage.expectYamlContains("io.kestra.plugin.core.trigger.Schedule");
            await noCodePage.expectYamlContains("my_schedule");
        });

        await test.step("save and confirm no error", async () => {
            await noCodePage.saveFlow();
        });
    });

    test("should create a pluginDefault via the no-code editor", async () => {
        await test.step("add a pluginDefault for Log", async () => {
            await noCodePage.addEntry(
                "pluginDefaults",
                "io.kestra.plugin.core.log.Log",
            );
        });

        await test.step("verify YAML contains the pluginDefault", async () => {
            await noCodePage.expectYamlContains("pluginDefaults");
            await noCodePage.expectYamlContains("io.kestra.plugin.core.log.Log");
        });

        await test.step("save and confirm no error", async () => {
            await noCodePage.saveFlow();
        });
    });

    test("should be able to add and edit tasks in a second flow", async ({request, baseURL}) => {
        // Create a second flow for cross-flow testing
        const secondFlowsApi = new FlowsApi(request, baseURL);
        const secondFlowId = await secondFlowsApi.generateFlowViaApi(
            "nocode-empty.yaml",
            "nocode-empty",
        );

        try {
            await test.step("navigate to second flow and open no-code panel", async () => {
                await noCodePage.gotoFlowEdit(shared.namespace, secondFlowId);
                await noCodePage.openNoCodePanel();
            });

            await test.step("create a Log task in the second flow", async () => {
                await noCodePage.addEntry(
                    "tasks",
                    "io.kestra.plugin.core.log.Log",
                    "second_flow_log",
                );
            });

            await test.step("verify YAML of the second flow", async () => {
                await noCodePage.expectYamlContains("io.kestra.plugin.core.log.Log");
                await noCodePage.expectYamlContains("second_flow_log");
                // Confirm the second flow id is in the YAML, not the first
                await noCodePage.expectYamlContains(secondFlowId);
            });

            await test.step("save the second flow without error", async () => {
                await noCodePage.saveFlow();
            });

            await test.step("add a trigger in the second flow", async () => {
                await noCodePage.page.getByRole("button", {name: "No-code"}).click();
                await noCodePage.openNoCodePanel();
                await noCodePage.addEntry(
                    "triggers",
                    "io.kestra.plugin.core.trigger.Schedule",
                    "second_schedule",
                );
                await noCodePage.expectYamlContains("second_schedule");
                await noCodePage.saveFlow();
            });
        } finally {
            await secondFlowsApi.removeFlowsViaApi();
        }
    });
});
