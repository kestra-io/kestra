import {expect, test} from "./blocks.fixture"
import type {Page} from "@playwright/test"
import {FlowsApi} from "../api/flows.api"
import {expectRing, goToSectionViaPalette, login, openBlockEditor, walkTo} from "./blocks.helpers"

// Scrolling behaviour of the canvas, which only exists once the content is
// taller than the viewport — hence the tall fixture, and hence a file of its
// own: blocks-navigation.spec.ts drives the blocks-canvas fixture, and a
// describe block only gets one beforeEach.
test.describe("Block editor — canvas scrolling", () => {
    let flowsApi: FlowsApi
    let flowId: string

    test.beforeEach(async ({page, request, baseURL}) => {
        flowsApi = new FlowsApi(request, baseURL)
        flowId = await flowsApi.generateFlowViaApi("blocks-tall.yaml", "blocks-tall-fixture")
        await login(page)
        await openBlockEditor(page, flowId)

        // Everything below measures a scroll, so prove there is one to measure.
        const overflows = await page.locator("[data-test='block-editor-scrollport']").evaluate(
            (el) => el.scrollHeight > el.clientHeight + 50,
        )
        expect(overflows, "fixture must be tall enough to scroll").toBe(true)
    })

    test.afterEach(async () => {
        await flowsApi.removeFlowsViaApi()
    })

    async function boxOf(page: Page, selector: string) {
        const box = await page.locator(selector).boundingBox()
        expect(box, `${selector} must be laid out`).not.toBeNull()
        return box!
    }

    test("a Go to jump centres its destination in the canvas", async ({page}) => {
        // Errors sits far enough down to need a scroll, and far enough from the
        // end that there is content below it to centre against.
        await goToSectionViaPalette(page, "Errors")
        await expectRing(page, "err_handler")

        const scrollport = await boxOf(page, "[data-test='block-editor-scrollport']")
        const target = await boxOf(page, "[data-block-id='err_handler']")

        // Asserting the distance from the centre, not merely "not at the bottom":
        // a revert to `nearest` parks it at the edge, and `start` would pin it to
        // the top — both of which a one-sided bound would let through.
        //
        // A quarter of the scrollport rather than something tighter because
        // centring is bounded by the content below the target: once there is
        // less than half a scrollport left, the browser cannot centre any
        // further. A tighter bound would go red on a viewport change rather than
        // on a regression.
        const targetCentre = target.y + target.height / 2
        const scrollportCentre = scrollport.y + scrollport.height / 2
        expect(Math.abs(targetCentre - scrollportCentre)).toBeLessThan(scrollport.height * 0.25)
    })

    test("a Go to jump to the final block scrolls as far as it can without clipping it", async ({page}) => {
        // `notify` is the last stop on the canvas — afterExecution is the last
        // lane buildSectionLanes emits — so there is nothing beneath it to
        // centre against and the scroll stops at its maximum. What must still
        // hold is that it is fully visible and clear of the status bar, which is
        // the failure this PR exists to fix.
        await goToSectionViaPalette(page, "After Execution")
        await expectRing(page, "notify")

        const scrollport = await boxOf(page, "[data-test='block-editor-scrollport']")
        const statusBar = await boxOf(page, "[data-test='block-editor-footer']")
        const target = await boxOf(page, "[data-block-id='notify']")

        expect(target.y).toBeGreaterThanOrEqual(scrollport.y)
        expect(target.y + target.height).toBeLessThanOrEqual(statusBar.y)
    })

    test("arrow-stepping to the final block keeps it clear of the status bar", async ({page}) => {
        // The same block as above, reached the other way. Stepping stays on
        // `nearest`, which stops as soon as the card is technically inside the
        // scrollport — including the strip the status bar is painted over. Only
        // the last block forces that case: anything higher has content below it
        // that pushes it clear anyway, so the assertion would hold whether or not
        // scroll-padding-bottom existed.
        await walkTo(page, "notify")
        await expectRing(page, "notify")

        const target = await boxOf(page, "[data-block-id='notify']")
        const statusBar = await boxOf(page, "[data-test='block-editor-footer']")

        expect(target.y + target.height).toBeLessThanOrEqual(statusBar.y)
    })

    test("jumping back up to Tasks leaves the first block fully visible", async ({page}) => {
        await goToSectionViaPalette(page, "Errors")
        await expectRing(page, "err_handler")

        await goToSectionViaPalette(page, "Tasks")
        await expectRing(page, "task_01")

        const scrollport = await boxOf(page, "[data-test='block-editor-scrollport']")
        const target = await boxOf(page, "[data-block-id='task_01']")

        expect(target.y).toBeGreaterThanOrEqual(scrollport.y)
        expect(target.y + target.height).toBeLessThanOrEqual(scrollport.y + scrollport.height)
    })
})
