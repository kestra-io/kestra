import {expect, test} from "./blocks.fixture"
import {FlowsApi} from "../api/flows.api"
import {expectRing, login, openBlockEditor} from "./blocks.helpers"

// Jumping across the canvas from the command palette, on a flow long enough that
// the destination starts off screen.
test.describe("Block editor — canvas navigation", () => {
    let flowsApi: FlowsApi
    let flowId: string

    test.beforeEach(async ({page, request, baseURL}) => {
        flowsApi = new FlowsApi(request, baseURL)
        flowId = await flowsApi.generateFlowViaApi("blocks-tall.yaml", "blocks-tall-fixture")
        await login(page)
        await openBlockEditor(page, flowId)
    })

    test.afterEach(async () => {
        await flowsApi.removeFlowsViaApi()
    })

    async function goTo(page: Parameters<typeof expectRing>[0], section: string) {
        await page.keyboard.press("ControlOrMeta+Shift+P")
        const menuInput = page.getByPlaceholder("Type a command or search a task…")
        await expect(menuInput).toBeFocused()
        await menuInput.fill(section)
        // The same term also matches "Insert <section>", so activate the goto
        // entry itself rather than pressing Enter on whatever sorted first.
        await page.getByText(`Go to ${section}`, {exact: true}).click()
    }

    test("a Go to jump leaves its destination comfortably in view", async ({page}) => {
        // Sanity: the canvas really does overflow, otherwise nothing below would
        // be measuring a scroll at all.
        const overflows = await page.locator(".block-editor-main").evaluate(
            (el) => el.scrollHeight > el.clientHeight + 50,
        )
        expect(overflows, "fixture must be tall enough to scroll").toBe(true)

        await goTo(page, "Errors")
        await expectRing(page, "err_handler")

        const box = await page.locator("[data-block-id='err_handler']").boundingBox()
        const viewport = page.viewportSize()
        expect(box).not.toBeNull()
        expect(viewport).not.toBeNull()

        // The regression parked the destination flush against the bottom edge,
        // partly behind the status bar. Centring keeps it well clear.
        expect(box!.y).toBeLessThan(viewport!.height * 0.75)
        expect(box!.y + box!.height).toBeLessThan(viewport!.height)
    })

    test("jumping back up to Tasks is visible too", async ({page}) => {
        await goTo(page, "Errors")
        await expectRing(page, "err_handler")

        await goTo(page, "Tasks")
        await expectRing(page, "task_01")

        const box = await page.locator("[data-block-id='task_01']").boundingBox()
        const viewport = page.viewportSize()
        expect(box!.y).toBeGreaterThanOrEqual(0)
        expect(box!.y + box!.height).toBeLessThan(viewport!.height)
    })
})
