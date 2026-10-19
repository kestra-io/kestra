import {FlowsApi} from "../api/flows.api"
import {ExecutionsApi} from "../api/executions.api"
import {expect, test} from "../fixtures/executions.fixture"

const BRUSH_START_KEY = "filters[startDate][GREATER_THAN_OR_EQUAL_TO]"
const BRUSH_END_KEY = "filters[endDate][LESS_THAN_OR_EQUAL_TO]"
const TIME_RANGE_KEY = "filters[timeRange][EQUALS]"

test("dragging across the logs chart filters the logs to that window, Escape clears it", async ({page, request, baseURL}) => {
    test.setTimeout(120000)
    const tenant = process.env.E2E_TENANT ?? "main"
    const flowsApi = new FlowsApi(request, baseURL)
    const flowId = await flowsApi.generateFlowViaApi("hello.yaml", "my-hello-flow-1")
    const executionsApi = new ExecutionsApi(request, flowId, baseURL)

    try {
        await executionsApi.generateExecutionViaApi()
        await page.goto(`/ui/${tenant}/logs?${TIME_RANGE_KEY}=PT1H&filters[flowId][EQUALS]=${flowId}`)
        await expect(async () => {
            await page.getByRole("button", {name: "Refresh data"}).click()
            await expect(page.getByText("Hello input: test").first()).toBeVisible({timeout: 2000})
        }).toPass({timeout: 45000})

        const chart = page.locator("[data-test='time-series-canvas']")
        await expect(chart.locator("canvas")).toBeVisible()
        const box = (await chart.boundingBox())!
        const y = box.y + box.height / 2
        await chart.hover({trial: true, position: {x: box.width * 0.2, y: box.height / 2}})

        const filteredSearch = page.waitForRequest((req) =>
            req.url().includes("/logs/search") && decodeURIComponent(req.url()).includes(BRUSH_START_KEY),
        )
        await page.mouse.move(box.x + box.width * 0.2, y)
        await page.mouse.down()
        await page.mouse.move(box.x + box.width * 0.8, y, {steps: 8})
        await page.mouse.up()
        await filteredSearch

        const brushed = new URL(page.url()).searchParams
        expect(brushed.get(TIME_RANGE_KEY)).toBeNull()
        expect(Date.parse(brushed.get(BRUSH_END_KEY)!)).toBeGreaterThan(Date.parse(brushed.get(BRUSH_START_KEY)!))
        await expect(page.getByText("Hello input: test").first()).toBeVisible()

        await page.keyboard.press("Escape")
        await expect.poll(() => new URL(page.url()).searchParams.get(TIME_RANGE_KEY)).toBe("PT24H")
        expect(new URL(page.url()).searchParams.get(BRUSH_START_KEY)).toBeNull()

        await chart.hover({trial: true, position: {x: box.width / 2, y: box.height / 2}})
        await page.mouse.click(box.x + box.width / 2, y)
        await expect.poll(() => new URL(page.url()).searchParams.get(BRUSH_START_KEY)).not.toBeNull()
        await expect(page.getByRole("dialog")).toHaveCount(0)
    } finally {
        await executionsApi.removeExecutionsViaApi()
        await flowsApi.removeFlowsViaApi()
    }
})
