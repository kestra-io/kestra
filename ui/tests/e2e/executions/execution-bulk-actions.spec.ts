import {test, expect} from "../fixtures/executions.fixture";
import {ExecutionState, Pagination} from "../pages/base.page";

test.describe("Executions' view Bulk Actions", () => {
    // Use specific flow to create executions
    test.use({flow: {fileName: "hello.yaml", flowId: "my-hello-flow-1"}});
    test("Labels changed only on a filtered set of executions when using Select All", async ({executionsPage, executionsApi, page}) => {
        test.slow(); // creating many executions
        expect(page.getByRole("heading", {name: "Executions"})).toBeVisible();

        await test.step("Generate 26 executions with the 'foo:bar' label and a single 'a:b' one", async () => {
            for (let i = 0; i < 26; i++) {
                await executionsApi.generateExecutionViaApi([["foo", "bar"]]);
            }
            await executionsApi.generateExecutionViaApi([["a", "b"]]);
        });

        await test.step("Filter just the executions featuring the 'foo:bar' label", async () => {
            await executionsPage.setPaginationTo(Pagination.ITEMS_25);
            await executionsPage.setFilterByFlowId(executionsApi.flowId);
            await executionsPage.setFilterByLabel("foo", "bar");

            await executionsPage.expectCountOfExecutionsToBe(25)
            await executionsPage.expectTotalExecutionsCountToBe(26);
        });

        await test.step("Set label to 'foo:baz' using Select All on filtered 'foo:bar' executions", async () => {
            await page.waitForTimeout(1500); // somehow the execution selection de-selects itself due a data load
            await executionsPage.selectExecutionRowByNumber();
            await executionsPage.clickOnSelectAll();
            await executionsPage.clickOnSetLabels();
            await executionsPage.setLabelOnSelectedExecutions();

            await executionsPage.expectCountOfExecutionsToBe(0)
        });

        await test.step("Switch filter to label 'a:b' which should not be affected by the label change", async () => {
            await executionsPage.removeFilterByLabelKey("foo"); 
            await executionsPage.setFilterByLabel("a", "b");

            await executionsPage.expectCountOfExecutionsToBe(1)
        });
    });

    test.use({flow: {fileName: "failure-then-success.yaml", flowId: "failure-then-success"}});
    test("Restart only on a filtered set of executions when using Select All", async ({executionsPage, executionsApi, page}) => {
        test.slow(); // creating and resuming many executions
        expect(page.getByRole("heading", {name: "Executions ", exact: true})).toBeVisible();

        await test.step("Generate 26 executions with the 'foo:bar' label and a single 'a:b' one", async () => {
            for (let i = 0; i < 26; i++) {
                await executionsApi.generateExecutionViaApi([["foo", "bar"]]);
            }
            await executionsApi.generateExecutionViaApi([["a", "b"]]);
        });

        await test.step("Filter just 'FAILED' executions featuring the 'foo:bar' label", async () => {
            await executionsPage.setPaginationTo(Pagination.ITEMS_25);
            await executionsPage.setFilterByFlowId(executionsApi.flowId);
            await executionsPage.setFilterByLabel("foo", "bar");
            await executionsPage.setFilterByState(ExecutionState.FAILED);

            await executionsPage.expectCountOfExecutionsToBe(25)
            expect(await executionsPage.getTotalExecutionsCount()).toEqual(26);
        });

        await test.step("Call Restart using Select All on filtered 'FAILED' & 'foo:bar' executions", async () => {
            await page.waitForTimeout(1500); // somehow the execution selection de-selects itself due a data load
            await executionsPage.selectExecutionRowByNumber();
            await executionsPage.clickOnSelectAll();
            await executionsPage.clickOnRestart();
        });

        await test.step("Show all 26 now successfully finished 'foo:bar' executions on a single page", async () => {
            await page.waitForTimeout(2000); // ensure restarted executions finished
            await executionsPage.setFilterByState(ExecutionState.SUCCESS);
            await executionsPage.setPaginationTo(Pagination.ITEMS_50);

            await executionsPage.expectCountOfExecutionsToBe(26)
        });

        await test.step("Switch filter to label 'a:b' which should not be affected by the Restart action", async () => {
            await executionsPage.removeFilterByLabelKey("foo");
            await executionsPage.setFilterByLabel("a", "b");
            await executionsPage.setFilterByState(ExecutionState.FAILED);

            await executionsPage.expectCountOfExecutionsToBe(1)
        });
    });

    test.use({flow: {fileName: "failure-then-success.yaml", flowId: "failure-then-success"}});
    test("Replay only on a filtered set of executions when using Select All", async ({executionsPage, executionsApi, page}) => {
        test.slow(); // creating and resuming many executions
        expect(page.getByRole("heading", {name: "Executions"})).toBeVisible();

        await test.step("Generate 26 executions with the 'foo:bar' label and a single 'a:b' one", async () => {
            for (let i = 0; i < 26; i++) {
                await executionsApi.generateExecutionViaApi([["foo", "bar"]]);
            }
            await executionsApi.generateExecutionViaApi([["a", "b"]]);
        });

        await test.step("Filter just 'FAILED' executions featuring the 'foo:bar' label", async () => {
            await executionsPage.setPaginationTo(Pagination.ITEMS_25);
            await executionsPage.setFilterByFlowId(executionsApi.flowId);
            await executionsPage.setFilterByLabel("foo", "bar");
            await executionsPage.setFilterByState(ExecutionState.FAILED);

            await executionsPage.expectCountOfExecutionsToBe(25)
            expect(await executionsPage.getTotalExecutionsCount()).toEqual(26);
        });

        await test.step("Call Replay using Select All on filtered 'FAILED' & 'foo:bar' executions", async () => {
            await page.waitForTimeout(1500); // somehow the execution selection de-selects itself due a data load
            await executionsPage.selectExecutionRowByNumber();
            await executionsPage.clickOnSelectAll();
            await executionsPage.clickOnReplay();
        });

        await test.step("Show 26 original and 26 replayed 'foo:bar' executions on a single page", async () => {
            await page.waitForTimeout(2000); // ensure replayed executions finished
            await executionsPage.setPaginationTo(Pagination.ITEMS_100);

            expect(await executionsPage.getCountOfDisplayedExecutions()).toEqual(26 * 2);
        });

        await test.step("Switch filter to label 'a:b' which should not be affected by the Restart action", async () => {
            await executionsPage.removeFilterByLabelKey("foo");
            await executionsPage.setFilterByLabel("a", "b");
            await executionsPage.setFilterByState(ExecutionState.FAILED);

            await executionsPage.expectCountOfExecutionsToBe(1)
        });
    });
});