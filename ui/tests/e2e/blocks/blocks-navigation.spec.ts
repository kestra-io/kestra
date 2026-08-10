import {expect, test} from "./blocks.fixture"
import {FlowsApi} from "../api/flows.api"
import {backToCanvas, expectRing, login, openBlockEditor, ringId, walkTo} from "./blocks.helpers"

// Keyboard-only navigation across the whole block editor canvas: the flow is
// the blocks-canvas fixture (trigger, Sequential with two children plus its
// own empty errors/finally lanes, two leaf tasks, empty top-level
// errors/finally sections).
test.describe("Block editor — keyboard navigation", () => {
    let flowsApi: FlowsApi
    let flowId: string

    test.beforeEach(async ({page, request, baseURL}) => {
        flowsApi = new FlowsApi(request, baseURL)
        flowId = await flowsApi.generateFlowViaApi("blocks-canvas.yaml", "blocks-canvas-fixture")
        await login(page)
        await openBlockEditor(page, flowId)
    })

    test.afterEach(async () => {
        await flowsApi.removeFlowsViaApi()
    })

    test("walks every stop of the canvas in order and wraps around", async ({page}) => {
        const expected = [
            "schedule_trigger",
            "seq_group",
            "child_one",
            "child_two",
            "__lane:tasks[0].errors",
            "__lane:tasks[0].finally",
            "middle_task",
            "last_task",
            "__section:errors",
            "__section:finally",
            "__section:afterExecution",
            "schedule_trigger", // wrap-around
        ]
        for (const stop of expected) {
            await page.keyboard.press("ArrowDown")
            await expectRing(page, stop)
        }
    })

    test("walks backwards from the bottom with ArrowUp", async ({page}) => {
        await page.keyboard.press("ArrowUp")
        await expectRing(page, "__section:afterExecution")
        await page.keyboard.press("ArrowUp")
        await expectRing(page, "__section:finally")
        await page.keyboard.press("ArrowUp")
        await expectRing(page, "__section:errors")
        await page.keyboard.press("ArrowUp")
        await expectRing(page, "last_task")
    })

    test("steps into a group with ArrowRight and back to the parent with ArrowLeft", async ({page}) => {
        await walkTo(page, "seq_group")

        await page.keyboard.press("ArrowRight")
        await expectRing(page, "child_one")

        await page.keyboard.press("ArrowLeft")
        await expectRing(page, "seq_group")
    })

    test("ArrowLeft on an expanded group collapses it, ArrowRight re-expands", async ({page}) => {
        await walkTo(page, "seq_group")

        await page.keyboard.press("ArrowLeft")
        await expect(page.locator("[data-block-id='child_one']")).toBeHidden()

        await page.keyboard.press("ArrowRight")
        await expect(page.locator("[data-block-id='child_one']")).toBeVisible()
    })

    test("Tab enters the canvas on its first card and a single Tab exits the whole composite", async ({page}) => {
        // Entry: the canvas container is the composite's single Tab stop and
        // delegates to the first card
        await page.locator("[data-test='block-editor-canvas']").focus()
        await expectRing(page, "schedule_trigger")
        expect(await page.evaluate(() => document.activeElement?.getAttribute("data-block-id"))).toBe("schedule_trigger")

        // Arrows move REAL focus along with the ring
        await page.keyboard.press("ArrowDown")
        await expectRing(page, "seq_group")
        expect(await page.evaluate(() => document.activeElement?.closest("[data-block-id]")?.getAttribute("data-block-id"))).toBe("seq_group")

        // Exit: one Tab leaves the composite entirely (children, lane buttons
        // and per-card actions are not tab stops)
        await page.keyboard.press("Tab")
        expect(await page.evaluate(() => !!document.activeElement?.closest("[data-block-id]"))).toBe(false)

        // The ring survives as the memory of the position
        expect(await ringId(page)).toBe("seq_group")
    })

    test("clicking a card syncs the keyboard focus ring onto it", async ({page}) => {
        // Clicking selects AND opens the block as a same-place tab; the ring
        // is the canvas's memory of the click once the user comes back to it.
        await page.locator("[data-block-id='middle_task']").click()
        await expect(page.getByRole("tab", {name: /middle_task/})).toBeVisible()
        await backToCanvas(page)
        await expectRing(page, "middle_task")

        // and keyboard navigation continues from there
        await page.keyboard.press("ArrowDown")
        await expectRing(page, "last_task")
    })

    test("Enter opens the block's editor in the shared flow-editor dock", async ({page}) => {
        // The dock's own split/tile/drag-drop pane navigation is covered by
        // MultiPanelTabs.vue's own tests — this only proves the merge's contract:
        // opening a block hands off to that shared dock instead of a bespoke one.
        await walkTo(page, "middle_task")
        await page.keyboard.press("Enter")

        await expect(page.getByRole("tab", {name: /middle_task/})).toBeVisible()
        await expect(page.locator(".task-edit-col-inputs")).toBeVisible()
    })

    test("the help overlay opens with ? and closes with Escape", async ({page}) => {
        await page.keyboard.press("Shift+?")
        await expect(page.getByText("Keyboard shortcuts").first()).toBeVisible()

        await page.keyboard.press("Escape")
        await expect(page.getByText("Keyboard shortcuts").first()).toBeHidden()
    })
})
