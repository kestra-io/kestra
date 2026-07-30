import {test, expect} from "../fixtures/executions.fixture"
import {ExecutionState, Pagination} from "../pages/base.page"

test.describe("Executions' view Bulk Actions", () => {

    // Each test drives 27 real flow executions against the single shared Kestra instance,
    // so keep them in one worker. `default` rather than `serial`: `serial` would skip the
    // remaining tests after a failure and hide a second regression.
    test.describe.configure({mode: "default"})

    test.describe("Set labels", () => {
        test.use({flow: {fileName: "hello.yaml", flowId: "my-hello-flow-1"}})

        test("Labels changed only on a filtered set of executions when using Select All", async ({executionsPage, executionsApi, page}) => {
            test.slow() // creating many executions
            expect(page.getByRole("heading", {name: "Executions"})).toBeVisible()

            await test.step("Generate 26 executions with the 'foo:bar' label and a single 'a:b' one", async () => {
                for (let i = 0; i < 26; i++) {
                    await executionsApi.generateExecutionViaApi([["foo", "bar"]])
                }
                await executionsApi.generateExecutionViaApi([["a", "b"]])
                await page.reload()
            })

            await test.step("Filter just the executions featuring the 'foo:bar' label", async () => {
                await executionsPage.setPaginationTo(Pagination.ITEMS_25)
                await executionsPage.setFilterByFlowId(executionsApi.flowId)
                await executionsPage.setFilterByLabel("foo", "bar")

                await executionsPage.expectCountOfExecutionsToBe(25)
                await executionsPage.expectTotalExecutionsCountToBe(26)
            })

            await test.step("Set label to 'foo:baz' using Select All on filtered 'foo:bar' executions", async () => {
                await executionsPage.selectExecutionRowByNumber()
                await executionsPage.clickOnSelectAll()
                await executionsPage.clickOnSetLabels()
                await executionsPage.setLabelOnSelectedExecutions()

                await executionsPage.expectCountOfExecutionsToBe(0)
            })

            await test.step("Switch filter to label 'a:b' which should not be affected by the label change", async () => {
                await executionsPage.removeFilterByLabelKey("foo")
                await executionsPage.setFilterByLabel("a", "b")

                await executionsPage.expectCountOfExecutionsToBe(1)
            })
        })
    })

    test.describe("Restart", () => {
        test.use({flow: {fileName: "failure-then-success.yaml", flowId: "failure-then-success"}})

        test("Restart only on a filtered set of executions when using Select All", async ({executionsPage, executionsApi, page}) => {
            test.slow() // creating and resuming many executions
            expect(page.getByRole("heading", {name: "Executions ", exact: true})).toBeVisible()

            await test.step("Generate 26 executions with the 'foo:bar' label and a single 'a:b' one", async () => {
                for (let i = 0; i < 26; i++) {
                    await executionsApi.generateExecutionViaApi([["foo", "bar"]])
                }
                await executionsApi.generateExecutionViaApi([["a", "b"]])
                await page.reload()
            })

            await test.step("Filter just 'FAILED' executions featuring the 'foo:bar' label", async () => {
                await executionsPage.setPaginationTo(Pagination.ITEMS_25)
                await executionsPage.setFilterByFlowId(executionsApi.flowId)
                await executionsPage.setFilterByLabel("foo", "bar")
                await executionsPage.setFilterByState(ExecutionState.FAILED)

                await executionsPage.expectCountOfExecutionsToBe(25)
                expect(await executionsPage.getTotalExecutionsCount()).toEqual(26)
            })

            await test.step("Call Restart using Select All on filtered 'FAILED' & 'foo:bar' executions", async () => {
                await executionsPage.selectExecutionRowByNumber()
                await executionsPage.clickOnSelectAll()
                await executionsPage.clickOnRestart()
            })

            await test.step("Count all 26 'foo:bar' executions as successfully finished", async () => {
                await executionsPage.setFilterByState(ExecutionState.SUCCESS)

                // Restart is asynchronous — reload until the server has finished all 26.
                await executionsPage.expectTotalExecutionsCountToBeAfterRefresh(26)
            })

            await test.step("Switch filter to label 'a:b' which should not be affected by the Restart action", async () => {
                await executionsPage.removeFilterByLabelKey("foo")
                await executionsPage.setFilterByLabel("a", "b")
                await executionsPage.setFilterByState(ExecutionState.FAILED)

                await executionsPage.expectCountOfExecutionsToBe(1)
            })
        })
    })

    test.describe("Replay", () => {
        test.use({flow: {fileName: "failure-then-success.yaml", flowId: "failure-then-success"}})

        test("Replay only on a filtered set of executions when using Select All", async ({executionsPage, executionsApi, page}) => {
            test.slow() // creating and resuming many executions
            expect(page.getByRole("heading", {name: "Executions"})).toBeVisible()

            await test.step("Generate 26 executions with the 'foo:bar' label and a single 'a:b' one", async () => {
                for (let i = 0; i < 26; i++) {
                    await executionsApi.generateExecutionViaApi([["foo", "bar"]])
                }
                await executionsApi.generateExecutionViaApi([["a", "b"]])
                await page.reload()
            })

            await test.step("Filter just 'FAILED' executions featuring the 'foo:bar' label", async () => {
                await executionsPage.setPaginationTo(Pagination.ITEMS_25)
                await executionsPage.setFilterByFlowId(executionsApi.flowId)
                await executionsPage.setFilterByLabel("foo", "bar")
                await executionsPage.setFilterByState(ExecutionState.FAILED)

                await executionsPage.expectCountOfExecutionsToBe(25)
                expect(await executionsPage.getTotalExecutionsCount()).toEqual(26)
            })

            await test.step("Call Replay using Select All on filtered 'FAILED' & 'foo:bar' executions", async () => {
                await executionsPage.selectExecutionRowByNumber()
                await executionsPage.clickOnSelectAll()
                await executionsPage.clickOnReplay()
            })

            await test.step("Count 26 original and 26 replayed 'foo:bar' executions", async () => {
                // Replay is asynchronous — reload until the 26 replays have joined the originals.
                await executionsPage.expectTotalExecutionsCountToBeAfterRefresh(26 * 2)
            })

            await test.step("Switch filter to label 'a:b' which should not be affected by the Restart action", async () => {
                await executionsPage.removeFilterByLabelKey("foo")
                await executionsPage.setFilterByLabel("a", "b")
                await executionsPage.setFilterByState(ExecutionState.FAILED)

                await executionsPage.expectCountOfExecutionsToBe(1)
            })
        })
    })
})
