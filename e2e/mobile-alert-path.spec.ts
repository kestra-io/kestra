import {test as anonymousTest, type APIRequestContext, type Page} from "@playwright/test"
import {test, expect} from "./fixtures/executions.fixture"
import {PRODUCT_TOUR_STORAGE_KEY, SKIPPED_PRODUCT_TOUR} from "./fixtures/auth"
import {shared} from "./fixtures/shared"
import {FlowsApi} from "./api/flows.api"
import {ExecutionsApi} from "./api/executions.api"

const tenant = process.env.E2E_TENANT ?? "main"

const byTest = (page: Page, name: string) => page.locator(`[data-test="${name}"]`)

const expectNoHorizontalOverflow = async (page: Page) => {
    const report = await page.evaluate(() => {
        const viewport = window.innerWidth
        const clipsHorizontally = (element: Element) => {
            const {overflowX} = getComputedStyle(element)
            return ["auto", "scroll", "hidden", "clip"].includes(overflowX) && element.getBoundingClientRect().right <= viewport + 1
        }
        const isClipped = (element: Element) => {
            for (let ancestor = element.parentElement; ancestor && ancestor.tagName !== "MAIN"; ancestor = ancestor.parentElement) {
                if (clipsHorizontally(ancestor)) return true
            }
            return false
        }
        const offenders = [...document.querySelectorAll("main *")]
            .filter((element) => {
                const rect = element.getBoundingClientRect()
                const style = getComputedStyle(element)
                return rect.width > 0 && style.visibility !== "hidden" && rect.right > viewport + 1 && !isClipped(element)
            })
            .slice(0, 5)
            .map((element) => `${element.tagName.toLowerCase()}.${element.className}`)
        return {scrollWidth: document.documentElement.scrollWidth, viewport, offenders}
    })

    expect(report.scrollWidth).toBeLessThanOrEqual(report.viewport)
    expect(report.offenders).toEqual([])
}

const openFailedExecution = async (request: APIRequestContext, baseURL: string | undefined) => {
    const flowsApi = new FlowsApi(request, baseURL)
    const flowId = await flowsApi.generateFlowViaApi("mobile-alert.yaml", "mobile-alert")
    const executionsApi = new ExecutionsApi(request, flowId, baseURL)
    const executionId = await executionsApi.generateExecutionViaApi()
    return {
        flowId,
        executionId,
        path: (tab: string) => `/ui/${tenant}/executions/${shared.namespace}/${flowId}/${executionId}/${tab}`,
        cleanup: async () => {
            await executionsApi.removeExecutionsViaApi()
            await flowsApi.removeFlowsViaApi()
        },
    }
}

test.describe("Alert to failed execution on a phone", () => {
    test.describe.configure({mode: "default"})

    test("no page of the path scrolls horizontally and the flow name stays in the top bar", async ({page, request, baseURL}) => {
        test.slow()
        const failed = await openFailedExecution(request, baseURL)

        try {
            for (const tab of ["overview", "gantt", "logs"]) {
                await test.step(`execution ${tab}`, async () => {
                    await page.goto(failed.path(tab))
                    await expect(page.getByRole("heading", {name: failed.flowId})).toBeVisible({timeout: 30000})
                    await expect(byTest(page, "bottom-action-bar")).toBeVisible()
                    await expectNoHorizontalOverflow(page)
                })
            }

            const lists = [
                {path: `/ui/${tenant}/dashboards`, ready: page.locator("main").getByText("Success Ratio")},
                {path: `/ui/${tenant}/executions`, ready: page.getByRole("row").nth(1)},
                {path: `/ui/${tenant}/flows`, ready: page.getByRole("row").nth(1)},
            ]
            for (const {path, ready} of lists) {
                await test.step(path, async () => {
                    await page.goto(path)
                    await expect(ready).toBeVisible({timeout: 30000})
                    await expectNoHorizontalOverflow(page)
                })
            }
        } finally {
            await failed.cleanup()
        }
    })

    test("a failed task leads from the overview to its logs", async ({page, request, baseURL}) => {
        test.slow()
        const failed = await openFailedExecution(request, baseURL)

        try {
            await page.goto(failed.path("overview"))
            await expect(byTest(page, "failed-task-card")).toContainText("reconcile", {timeout: 30000})

            await byTest(page, "view-task-logs").click()

            await expect(page).toHaveURL(/\/logs/)
            await expect(page.getByText("refusing to close the period").first()).toBeVisible()
            await expect(page.getByRole("button", {name: "Copy to clipboard"}).first()).toBeVisible()
        } finally {
            await failed.cleanup()
        }
    })

    test("the executions list renders one card per row", async ({page, request, baseURL}) => {
        test.slow()
        const failed = await openFailedExecution(request, baseURL)

        try {
            await page.goto(`/ui/${tenant}/executions?filters[flowId][EQUALS]=${failed.flowId}`)

            await expect(page.getByRole("row").filter({hasText: failed.flowId})).toBeVisible({timeout: 30000})
            const header = page.locator("thead th").first()
            await expect(header).toBeAttached()
            await expect(header).toBeHidden()
        } finally {
            await failed.cleanup()
        }
    })
})

anonymousTest.describe("Sign-in from an alert link on a phone", () => {
    anonymousTest.use({storageState: {cookies: [], origins: []}})

    anonymousTest("lands on the linked tab and says where it will land", async ({page, request, baseURL}) => {
        anonymousTest.slow()
        await page.addInitScript(([tourKey, tourState]) => {
            localStorage.setItem(tourKey, tourState)
        }, [PRODUCT_TOUR_STORAGE_KEY, SKIPPED_PRODUCT_TOUR])
        const failed = await openFailedExecution(request, baseURL)

        try {
            await page.goto(`/ui/login?from=${encodeURIComponent(failed.path("gantt").replace("/ui", ""))}`)

            const hint = byTest(page, "login-after-sign-in")
            await expect(hint).toContainText(`${shared.namespace} / ${failed.flowId}`)
            await expect(hint).toContainText("Gantt")

            await page.getByRole("textbox", {name: "Email"}).fill(shared.username)
            await page.getByRole("textbox", {name: "Password"}).fill(shared.password)
            await page.getByRole("button", {name: "Login"}).click()

            await expect(page).toHaveURL(new RegExp(`/executions/${shared.namespace}/${failed.flowId}/${failed.executionId}/gantt`), {timeout: 30000})
        } finally {
            await failed.cleanup()
        }
    })
})
