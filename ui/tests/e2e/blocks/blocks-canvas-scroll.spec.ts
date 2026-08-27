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
        // The section has a block, so the jump lands on it rather than on the
        // empty-section sentinel.
        await goToSectionViaPalette(page, "Errors")
        await expectRing(page, "err_handler")

        const scrollport = await boxOf(page, "[data-test='block-editor-scrollport']")
        const target = await boxOf(page, "[data-block-id='err_handler']")

        // Asserting the distance from the centre, not merely "not at the bottom":
        // a revert to `nearest` parks it at the edge, and `start` would pin it to
        // the top — both of which a one-sided bound would let through.
        //
        // The tolerance is a quarter of the scrollport rather than something
        // tighter because centring is bounded by the content below the target:
        // once there is less than half a scrollport left, the browser cannot
        // centre any further and stops short. A tighter bound here would go red
        // on a viewport change rather than on a regression.
        const targetCentre = target.y + target.height / 2
        const scrollportCentre = scrollport.y + scrollport.height / 2
        expect(Math.abs(targetCentre - scrollportCentre)).toBeLessThan(scrollport.height * 0.25)
    })

    test("a Go to jump to the last block scrolls as far as it can without clipping it", async ({page}) => {
        // The boundary the centring cannot reach: with less than half a
        // scrollport of content below it, the last block cannot be centred at
        // all. What must still hold is that it is fully visible and clear of the
        // status bar — the failure this PR exists to fix.
        await goToSectionViaPalette(page, "Finally")
        await expectRing(page, "cleanup")

        const scrollport = await boxOf(page, "[data-test='block-editor-scrollport']")
        const statusBar = await boxOf(page, "[data-test='block-editor-footer']")
        const target = await boxOf(page, "[data-block-id='cleanup']")

        expect(target.y).toBeGreaterThanOrEqual(scrollport.y)
        expect(target.y + target.height).toBeLessThanOrEqual(statusBar.y)
    })

    test("arrow-stepping to a block near the bottom keeps it clear of the status bar", async ({page}) => {
        // Stepping still uses `nearest`, which stops as soon as the card is
        // technically inside the scrollport — including the strip the status bar
        // is painted over. scroll-padding-bottom is what reserves that strip.
        await walkTo(page, "err_handler")
        await expectRing(page, "err_handler")

        const target = await boxOf(page, "[data-block-id='err_handler']")
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
