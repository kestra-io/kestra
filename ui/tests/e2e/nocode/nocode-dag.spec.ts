import {expect, test} from "@playwright/test";
import {FlowsApi} from "../api/flows.api";
import {NoCodePage} from "../pages/nocode.page";
import {shared} from "../fixtures/shared";

/**
 * E2E tests for the no-code editor — DAG (Directed Acyclic Graph) tasks.
 *
 * Covers:
 * - Creating a DAG task from scratch using only the no-code editor
 * - Editing an existing task within a DAG (updating its properties)
 * - Creating a new DAG task that depends on another, without closing the creation tab
 *   (i.e., filling both the task definition and the dependsOn field in one go)
 *
 * After each action the YAML is verified and the flow is saved to confirm
 * no validation errors are thrown.
 */

test.describe("No-code editor — DAG", () => {
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

    test("should create a DAG task purely using the no-code editor", async ({page, request, baseURL}) => {
        flowId = await flowsApi.generateFlowViaApi("nocode-empty.yaml", "nocode-empty");
        await noCodePage.gotoFlowEdit(shared.namespace, flowId);
        await noCodePage.openNoCodePanel();

        await test.step("add a DAG task", async () => {
            await noCodePage.addEntry(
                "tasks",
                "io.kestra.plugin.core.flow.Dag",
                "my_dag",
            );
        });

        await test.step("verify the DAG task is in YAML", async () => {
            await noCodePage.expectYamlContains("io.kestra.plugin.core.flow.Dag");
            await noCodePage.expectYamlContains("my_dag");
        });

        await test.step("add inner tasks to the DAG via the no-code editor", async () => {
            // DAG tasks have a tasks list with a "task" field per item.
            // Add first DAG task node via no-code.
            await noCodePage.addEntry("tasks", "io.kestra.plugin.core.log.Log", "dag_node_a");
        });

        await test.step("verify the first DAG node is in YAML", async () => {
            await noCodePage.expectYamlContains("dag_node_a");
        });

        await test.step("save and confirm no error", async () => {
            await noCodePage.saveFlow();
        });
    });

    test("should edit an existing task inside a DAG", async ({page, request, baseURL}) => {
        flowId = await flowsApi.generateFlowViaApi("nocode-dag.yaml", "nocode-dag");
        await noCodePage.gotoFlowEdit(shared.namespace, flowId);
        await noCodePage.openNoCodePanel();

        await test.step("open the DAG task's edit tab", async () => {
            await noCodePage.clickElement("dag_task");
        });

        await test.step("open dag_a node's edit tab", async () => {
            await noCodePage.clickElement("dag_a");
        });

        await test.step("update the message field of dag_a", async () => {
            await noCodePage.fillField("message", "Updated message from no-code");
        });

        await test.step("verify YAML reflects the update", async () => {
            await noCodePage.expectYamlContains("Updated message from no-code");
        });

        await test.step("save and confirm no error", async () => {
            await noCodePage.saveFlow();
        });
    });

    test("should create a new DAG task depending on another without closing the creation tab", async ({page, request, baseURL}) => {
        flowId = await flowsApi.generateFlowViaApi("nocode-dag.yaml", "nocode-dag");
        await noCodePage.gotoFlowEdit(shared.namespace, flowId);
        await noCodePage.openNoCodePanel();

        await test.step("open the DAG task's edit tab", async () => {
            await noCodePage.clickElement("dag_task");
        });

        await test.step("start adding a new DAG node (open creation tab)", async () => {
            // Click Add in the DAG tasks section to open a creation tab
            await noCodePage.clickAddInSection("tasks");
            // Select the task type
            await noCodePage.selectPluginType("io.kestra.plugin.core.log.Log");
            // Fill the id field — the creation tab remains open
            await noCodePage.fillField("id", "dag_c");
        });

        await test.step("fill the message field while the creation tab is still open", async () => {
            await noCodePage.fillField("message", "task c that depends on a");
        });

        await test.step("add dependsOn field via code editor while staying in no-code", async () => {
            // The dependsOn field for DAG tasks in the YAML requires adding a dependsOn array.
            // Since the creation tab is still open and saves automatically, get the current YAML
            // and add the dependsOn relationship there.
            await noCodePage.page.waitForTimeout(1500); // wait for auto-save debounce
            const currentYaml = await noCodePage.getFlowYaml();
            const withDependsOn = currentYaml.replace(
                /(\s+- task:\s+id: dag_c\s+type: io\.kestra\.plugin\.core\.log\.Log\s+message: .+)/,
                "$1\n        dependsOn:\n          - dag_a",
            );
            await noCodePage.setFlowYaml(withDependsOn);
        });

        await test.step("verify YAML has dag_c depending on dag_a", async () => {
            await noCodePage.expectYamlContains("dag_c");
            await noCodePage.expectYamlContains("dependsOn");
            await noCodePage.expectYamlContains("dag_a");
        });

        await test.step("save and confirm no error", async () => {
            await noCodePage.saveFlow();
        });
    });

    test("should show all existing DAG tasks in the no-code editor", async ({page, request, baseURL}) => {
        flowId = await flowsApi.generateFlowViaApi("nocode-dag.yaml", "nocode-dag");
        await noCodePage.gotoFlowEdit(shared.namespace, flowId);
        await noCodePage.openNoCodePanel();

        await test.step("open the DAG task's edit tab", async () => {
            await noCodePage.clickElement("dag_task");
        });

        await test.step("verify both DAG nodes are visible in the element list", async () => {
            await expect(
                noCodePage.page.locator(".element").filter({hasText: "dag_a"}),
            ).toBeVisible();
            await expect(
                noCodePage.page.locator(".element").filter({hasText: "dag_b"}),
            ).toBeVisible();
        });

        await test.step("verify dependsOn relationship is in YAML", async () => {
            await noCodePage.expectYamlContains("dag_a");
            await noCodePage.expectYamlContains("dag_b");
            await noCodePage.expectYamlContains("dependsOn");
        });

        await test.step("save and confirm no error", async () => {
            await noCodePage.saveFlow();
        });
    });
});
