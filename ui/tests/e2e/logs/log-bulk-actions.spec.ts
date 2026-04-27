import {test, expect} from "../fixtures/logs.fixture";
import {shared} from "../fixtures/shared";

test.describe("Logs view Bulk Actions", () => {

    test.use({flow: {fileName: "hello.yaml", flowId: "my-hello-flow-1"}, executionCount: 3});

    test("Bulk delete by ids removes only the selected logs", async ({logsPage, logsApi, executionsApi, page}) => {
        test.slow();
        await expect(page.getByRole("heading", {name: "Logs"})).toBeVisible();

        await test.step("Wait for logs to be indexed for the test flow", async () => {
            await expect.poll(
                async () => logsApi.countLogsForFlow(shared.namespace, executionsApi.flowId),
                {timeout: 15000}
            ).toBeGreaterThanOrEqual(3);
            await page.reload();
            await page.waitForLoadState("networkidle");
        });

        const initialDisplayed = await logsPage.getDisplayedLogCount();
        expect(initialDisplayed).toBeGreaterThanOrEqual(3);

        await test.step("Select 2 log rows individually", async () => {
            await logsPage.selectLogRowByIndex(0);
            await logsPage.selectLogRowByIndex(1);
            await logsPage.expectBulkSelectBarToShow(2);
        });

        await test.step("Confirm delete and verify exactly 2 logs were removed", async () => {
            await logsPage.clickOnDeleteAndConfirm();
            await logsPage.expectDisplayedLogCountToBe(initialDisplayed - 2);
            await logsPage.expectBulkSelectBarHidden();
        });
    });

    test.use({flow: {fileName: "hello.yaml", flowId: "my-hello-flow-1"}, executionCount: 3});

    test("Select All deletes every log matching the current filters", async ({logsPage, logsApi, executionsApi, page}) => {
        test.slow();
        await expect(page.getByRole("heading", {name: "Logs"})).toBeVisible();

        await test.step("Wait for logs to be indexed for the test flow", async () => {
            await expect.poll(
                async () => logsApi.countLogsForFlow(shared.namespace, executionsApi.flowId),
                {timeout: 15000}
            ).toBeGreaterThanOrEqual(3);
            await page.reload();
            await page.waitForLoadState("networkidle");
        });

        await test.step("Select one row then expand to Select All", async () => {
            await logsPage.selectLogRowByIndex(0);
            await logsPage.clickOnSelectAll();
        });

        await test.step("Confirm delete and verify backend has zero logs for this flow", async () => {
            await logsPage.clickOnDeleteAndConfirm();
            await expect.poll(
                async () => logsApi.countLogsForFlow(shared.namespace, executionsApi.flowId),
                {timeout: 10000}
            ).toBe(0);
            await logsPage.expectBulkSelectBarHidden();
        });
    });
});
