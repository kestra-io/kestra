import {FlowsApi} from "../api/flows.api"
import {ExecutionsApi} from "../api/executions.api"
import {expect, test} from "../fixtures/executions.fixture"

test("long webhook details scroll without moving the trigger on hover", async ({page, request, baseURL}) => {
    const tenant = process.env.E2E_TENANT ?? "main"
    const flowsApi = new FlowsApi(request, baseURL)
    const flowId = await flowsApi.generateFlowViaApi("trigger-popover.yaml", "trigger-popover")
    const executionsApi = new ExecutionsApi(request, flowId, baseURL)

    try {
        const execution = await executionsApi.generateWebhookExecutionViaApi(
            "test",
            Object.fromEntries(Array.from({length: 20}, (_, i) => [`X-Header-${i}`, "x".repeat(120)])),
        )

        for (const viewport of [{width: 1280, height: 720}, {width: 1440, height: 900}]) {
            await page.setViewportSize(viewport)
            await page.goto(`/ui/${tenant}/executions?filters[flowId][EQUALS]=${flowId}`)
            // Give overlay-scrollbar platforms the same width-changing scrollbars as Windows.
            await page.addStyleTag({content: "::-webkit-scrollbar { width: 15px; height: 15px; }"})
            const row = page.getByRole("row").filter({has: page.locator(`a[href$="/${execution.id}"]`)})
            const icon = row.getByRole("img", {name: "io.kestra.plugin.core.trigger.Webhook", exact: true})
            await expect(icon).toBeVisible()
            await icon.scrollIntoViewIfNeeded()
            await icon.evaluate(async element => {
                let previous = element.getBoundingClientRect().x
                let stableFrames = 0
                for (let frame = 0; frame < 60 && stableFrames < 3; frame++) {
                    await new Promise<void>(resolve => requestAnimationFrame(() => resolve()))
                    const current = element.getBoundingClientRect().x
                    stableFrames = Math.abs(current - previous) < 0.1 ? stableFrames + 1 : 0
                    previous = current
                }
                if (stableFrames < 3) throw new Error("Trigger position did not stabilize")
            })
            const before = await icon.boundingBox()
            expect(before).not.toBeNull()
            const width = await page.evaluate(() => document.documentElement.clientWidth)
            await page.mouse.move(before!.x + before!.width / 2, before!.y + before!.height / 2)
            const popover = page.getByRole("tooltip").filter({hasText: "Trigger details: webhook", visible: true})
            await expect(popover).toBeVisible()
            const title = popover.getByText("Trigger details: webhook", {exact: true})
            const details = popover.getByTestId("trigger-details")
            await expect.poll(() => details.evaluate(element => element.scrollHeight - element.clientHeight)).toBeGreaterThan(0)
            const box = await popover.boundingBox()
            expect(box).not.toBeNull()
            expect(box!.y).toBeGreaterThanOrEqual(0)
            expect(box!.y + box!.height).toBeLessThanOrEqual(viewport.height)

            const widths = await page.evaluate(async () => {
                const samples: number[] = []
                for (let frame = 0; frame < 30; frame++) {
                    await new Promise<void>(resolve => requestAnimationFrame(() => resolve()))
                    samples.push(document.documentElement.clientWidth)
                }
                return samples
            })
            expect(new Set(widths)).toEqual(new Set([width]))
            await expect(popover).toBeVisible()
            expect((await icon.boundingBox())!.x).toBeCloseTo(before!.x, 1)
            const titleY = (await title.boundingBox())!.y
            await details.evaluate(element => { element.scrollTop = element.scrollHeight })
            expect(await details.evaluate(element => element.scrollTop)).toBeGreaterThan(0)
            expect((await title.boundingBox())!.y).toBeCloseTo(titleY, 1)
            await page.mouse.move(0, 0)
            await expect(popover).toBeHidden()
        }
    } finally {
        await executionsApi.removeExecutionsViaApi()
        await flowsApi.removeFlowsViaApi()
    }
})
