import {expect, test} from "@playwright/test";
import {ExecutionsPage} from "../pages/executions.page";
import {ExecutionsApi} from "../api/executions.api";
import {FlowsApi} from "../api/flows.api";
import {ExecutionState} from "../pages/base.page";

/**
 * Verifies that the executions page loads correctly and displays execution data.
 * Tests basic page rendering, execution display, and execution filtering by flow ID.
 */
test("should display executions page with at least one execution", async ({page, request, baseURL}) => {
    const flowsApi = new FlowsApi(request, baseURL);
    const flowId = await flowsApi.generateFlowViaApi("hello.yaml", "my-hello-flow-1");

    const executionsApi = new ExecutionsApi(request, flowId, baseURL);
    await executionsApi.generateExecutionViaApi();

    const executionsPage = new ExecutionsPage(page);
    await executionsPage.goto();

    await test.step("Verify executions page loads correctly", async () => {
        await expect(page.getByRole("heading", {name: "Executions"})).toBeVisible();
    });

    await test.step("Verify at least one execution is displayed", async () => {
        const executionCount = await executionsPage.getCountOfDisplayedExecutions();
        expect(executionCount).toBeGreaterThan(0);
    });

    await test.step("Verify execution filtering works", async () => {
        await executionsPage.setFilterByFlowId(flowId);

        const filteredCount = await executionsPage.getCountOfDisplayedExecutions();
        expect(filteredCount).toBeGreaterThan(0);
    });

    await executionsApi.removeExecutionsViaApi();
    await flowsApi.removeFlowsViaApi();
});

/**
 * Tests bulk set labels functionality on selected executions.
 * Selects multiple executions and applies labels using the bulk action dropdown,
 * verifying that the labels are successfully added to the selected executions.
 */
test("should perform bulk set labels action on selected executions", async ({page, request, baseURL}) => {
    const flowsApi = new FlowsApi(request, baseURL);
    const flowId = await flowsApi.generateFlowViaApi("hello.yaml", "my-hello-flow-1");

    const executionsApi = new ExecutionsApi(request, flowId, baseURL);
    await executionsApi.generateExecutionViaApi();
    await executionsApi.generateExecutionViaApi();

    const executionsPage = new ExecutionsPage(page);
    await executionsPage.goto();

    // scope the test to this flow to avoid interference from other tests
    await executionsPage.setFilterByFlowId(flowId);
    await executionsPage.waitForDisplayedExecutionsCountAtLeast(2);

    await test.step("Select multiple executions and open set labels dialog", async () => {
        await executionsPage.selectExecutionRowByNumber(1);
        await executionsPage.selectExecutionRowByNumber(2);
        await page.waitForTimeout(500);
        await executionsPage.clickOnSetLabels();
    });

    await test.step("Set labels on selected executions", async () => {
        await executionsPage.setLabelOnSelectedExecutions();
    });

    await test.step("Verify labels were set on executions", async () => {
        await executionsPage.setFilterByLabel("foo", "baz");
        await executionsPage.waitForDisplayedExecutionsCountAtLeast(2);
    });

    await executionsApi.removeExecutionsViaApi();
    await flowsApi.removeFlowsViaApi();
});

/**
 * Tests bulk replay functionality on selected executions.
 * Selects multiple executions and triggers the replay action, verifying that
 * new executions are created as a result of the replay operation.
 */
test("should perform bulk replay action on selected executions", async ({page, request, baseURL}) => {
    const flowsApi = new FlowsApi(request, baseURL);
    const flowId = await flowsApi.generateFlowViaApi("hello.yaml", "my-hello-flow-1");

    const executionsApi = new ExecutionsApi(request, flowId, baseURL);
    await executionsApi.generateExecutionViaApi();
    await executionsApi.generateExecutionViaApi();

    const executionsPage = new ExecutionsPage(page);
    await executionsPage.goto();
    // scope to the flow and wait until at least two executions are visible
    await executionsPage.setFilterByFlowId(flowId);
    await executionsPage.waitForDisplayedExecutionsCountAtLeast(2);

    await test.step("Select executions and perform bulk replay", async () => {
        await executionsPage.selectExecutionRowByNumber(1);
        await executionsPage.selectExecutionRowByNumber(2);
        await page.waitForTimeout(1000);
        await executionsPage.clickOnReplay();
        await page.waitForTimeout(2000);
    });

    await test.step("Verify replay created new executions", async () => {
        await executionsPage.waitForDisplayedExecutionsCountAtLeast(4);
    });

    await executionsApi.removeExecutionsViaApi();
    await flowsApi.removeFlowsViaApi();
});

test("should perform bulk restart action on selected failed executions", async ({page, request, baseURL}) => {
    const flowsApi = new FlowsApi(request, baseURL);
    // Use a failing flow to verify restart behavior (fails first attempt, succeeds on restart)
    const flowId = await flowsApi.generateFlowViaApi("failure-then-success.yaml", "failure-then-success");

    const executionsApi = new ExecutionsApi(request, flowId, baseURL);
    await executionsApi.generateExecutionViaApi();
    await executionsApi.generateExecutionViaApi();

    const executionsPage = new ExecutionsPage(page);
    await executionsPage.goto();
    // scope to the failing flow and verify we have failed executions
    await executionsPage.setFilterByFlowId(flowId);
    await executionsPage.waitForDisplayedExecutionsCountAtLeast(2);
    await test.step("Verify executions are displayed and failed", async () => {
        await executionsPage.setFilterByState(ExecutionState.FAILED);
        await executionsPage.waitForDisplayedExecutionsCountAtLeast(2);
    });

    await test.step("Select executions and perform bulk restart", async () => {
        await executionsPage.selectExecutionRowByNumber(1);
        await executionsPage.selectExecutionRowByNumber(2);
        await page.waitForTimeout(1000);
        await executionsPage.clickOnRestart();
    });

    await test.step("Verify restart succeeded and status is SUCCESS", async () => {
        await executionsPage.setFilterByState(ExecutionState.SUCCESS);
        await executionsPage.waitForDisplayedExecutionsCountAtLeast(2, 30000);
    });

    await executionsApi.removeExecutionsViaApi();
    await flowsApi.removeFlowsViaApi();
});

/**
 * Tests bulk delete functionality on selected executions.
 * Creates multiple executions, selects them, triggers the delete action with
 * confirmation dialog, and verifies the executions are removed from the display.
 */
test("should perform bulk delete action on selected executions", async ({page, request, baseURL}) => {
    const flowsApi = new FlowsApi(request, baseURL);
    const flowId = await flowsApi.generateFlowViaApi("hello.yaml", "my-hello-flow-1");

    const executionsApi = new ExecutionsApi(request, flowId, baseURL);
    await executionsApi.generateExecutionViaApi();
    await executionsApi.generateExecutionViaApi();
    await executionsApi.generateExecutionViaApi();

    const executionsPage = new ExecutionsPage(page);
    await executionsPage.goto();

    await test.step("Filter to show only this flow's executions", async () => {
        await executionsPage.setFilterByFlowId(flowId);
        await executionsPage.waitForDisplayedExecutionsCountAtLeast(3);
    });

    await test.step("Perform bulk delete with confirmation", async () => {
        await executionsPage.selectExecutionRowByNumber(1);
        await executionsPage.selectExecutionRowByNumber(2);
        await page.waitForTimeout(1000);
        await executionsPage.clickOnDelete();
    });

    await test.step("Verify executions were deleted", async () => {
        await executionsPage.waitForDisplayedExecutionsCountBelow(3);
    });

    await executionsApi.removeExecutionsViaApi();
    await flowsApi.removeFlowsViaApi();
});