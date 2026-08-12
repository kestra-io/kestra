import {expect, test} from "./blocks.fixture"
import type {Page} from "@playwright/test"
import {FlowsApi} from "../api/flows.api"
import {expectRing, login, openBlockEditor, walkTo} from "./blocks.helpers"

// Moving around a canvas that is taller than the viewport. Both behaviours here
// only exist once the canvas actually scrolls, hence the tall fixture.
test.describe("Block editor — canvas navigation", () => {
    let flowsApi: FlowsApi
    let flowId: string

    test.beforeEach(async ({page, request, baseURL}) => {
        flowsApi = new FlowsApi(request, baseURL)
        flowId = await flowsApi.generateFlowViaApi("blocks-tall.yaml", "blocks-tall-fixture")
        await login(page)
        await openBlockEditor(page, flowId)

        // Everything below measures a scroll, so prove there is one to measure.
        const overflows = await page.locator(".block-editor-main").evaluate(
            (el) => el.scrollHeight > el.clientHeight + 50,
        )
        expect(overflows, "fixture must be tall enough to scroll").toBe(true)
    })

    test.afterEach(async () => {
        await flowsApi.removeFlowsViaApi()
    })

    async function goTo(page: Page, section: string) {
        await page.keyboard.press("ControlOrMeta+Shift+P")
        const menuInput = page.getByPlaceholder("Type a command or search a task…")
        await expect(menuInput).toBeFocused()
        await menuInput.fill(section)
        // The same term also matches "Insert <section>", so activate the goto
        // entry itself rather than pressing Enter on whatever sorted first.
        await page.getByText(`Go to ${section}`, {exact: true}).click()
    }

    async function boxOf(page: Page, selector: string) {
        const box = await page.locator(selector).boundingBox()
        expect(box, `${selector} must be laid out`).not.toBeNull()
        return box!
    }

    test("a Go to jump centres its destination in the canvas", async ({page}) => {
        // The section has a block, so the jump lands on it rather than on the
        // empty-section sentinel.
        await goTo(page, "Errors")
        await expectRing(page, "err_handler")

        const scrollport = await boxOf(page, ".block-editor-main")
        const target = await boxOf(page, "[data-block-id='err_handler']")

        // Asserting the distance from the centre, not merely "not at the bottom":
        // a revert to `nearest` parks it at the edge, and `start` would pin it to
        // the top — both of which a one-sided bound would let through.
        const targetCentre = target.y + target.height / 2
        const scrollportCentre = scrollport.y + scrollport.height / 2
        expect(Math.abs(targetCentre - scrollportCentre)).toBeLessThan(scrollport.height * 0.25)
    })

    test("arrow-stepping to the last block keeps it clear of the status bar", async ({page}) => {
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
        await goTo(page, "Errors")
        await expectRing(page, "err_handler")

        await goTo(page, "Tasks")
        await expectRing(page, "task_01")

        const scrollport = await boxOf(page, ".block-editor-main")
        const target = await boxOf(page, "[data-block-id='task_01']")

        expect(target.y).toBeGreaterThanOrEqual(scrollport.y)
        expect(target.y + target.height).toBeLessThanOrEqual(scrollport.y + scrollport.height)
    })
})
