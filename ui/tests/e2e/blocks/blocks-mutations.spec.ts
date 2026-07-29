import {expect, test} from "@playwright/test"
import {FlowsApi} from "../api/flows.api"
import {canvasCardIds, expectRing, fetchFlowSource, login, openBlockEditor, pickTask, saveFlow, taskIdsInOrder, walkTo, waitForRing} from "./blocks.helpers"

// Destructive/structural mutations (duplicate, delete + undo, reorder) and the
// split-view multi-pane behaviors, all keyboard-first.
test.describe("Block editor — mutations & split view", () => {
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

    test("d duplicates the focused block right after it", async ({page, request, baseURL}) => {
        await walkTo(page, "middle_task")
        await page.keyboard.press("d")

        await expect(page.locator("[data-block-id='middle_task_copy']")).toBeVisible()

        await saveFlow(page)
        const source = await fetchFlowSource(request, baseURL!, flowId)
        expect(taskIdsInOrder(source)).toEqual(["seq_group", "middle_task", "middle_task_copy", "last_task"])
    })

    test("Backspace deletes after an Enter-confirmed dialog, moves focus to the neighbor, and Undo restores", async ({page}) => {
        await walkTo(page, "middle_task")
        await page.keyboard.press("Backspace")

        // Dialog opens with focus on the Delete button and a REAL block name
        const dialog = page.locator(".kel-message-box")
        await expect(dialog).toBeVisible()
        await expect(dialog).toContainText("Delete middle_task?")
        await page.keyboard.press("Enter")

        // Gone, focus continues from the deletion point, undo pill offered
        await expect(page.locator("[data-block-id='middle_task']")).toBeHidden()
        await expectRing(page, "last_task")
        await page.locator("[data-test='block-editor-undo']").click()
        await expect(page.locator("[data-block-id='middle_task']")).toBeVisible()
    })

    test("the configure button opens a flowable's config form", async ({page}) => {
        const fid = await flowsApi.generateFlowViaApi("blocks-flowable.yaml", "blocks-flowable-fixture")
        await openBlockEditor(page, fid)
        await walkTo(page, "my_if")

        await page.locator("[data-test='flowable-cluster-configure']").click()

        const dock = page.locator("[data-test='block-editor-task-edit']")
        await expect(dock).toBeVisible()
        await expect(dock).toContainText("condition")
    })

    test("Ctrl/Cmd+Z undoes an inserted block", async ({page}) => {
        await walkTo(page, "middle_task")
        await page.keyboard.press("a")
        await pickTask(page, "fail", "Fail")
        const inserted = await waitForRing(page)
        await expect(page.locator(`[data-block-id='${inserted}']`)).toBeVisible()

        await page.keyboard.press("ControlOrMeta+z")
        await expect(page.locator(`[data-block-id='${inserted}']`)).toBeHidden()
        await expect(page.locator("[data-block-id='middle_task']")).toBeVisible()
    })

    test("Delete on an empty-section placeholder is a no-op", async ({page}) => {
        await walkTo(page, "__section:errors")
        await page.keyboard.press("Backspace")
        await expect(page.locator(".kel-message-box")).toBeHidden()
    })

    test("Alt+Arrow reorders the focused block and the order persists", async ({page, request, baseURL}) => {
        await walkTo(page, "middle_task")
        await page.keyboard.press("Alt+ArrowDown")
        await expectRing(page, "middle_task")

        await saveFlow(page)
        const source = await fetchFlowSource(request, baseURL!, flowId)
        expect(taskIdsInOrder(source)).toEqual(["seq_group", "last_task", "middle_task"])
    })

    test("opening blocks by default lands them as same-place tabs in the shared No-code pane", async ({page}) => {
        // The merge's default: a clicked block opens as a tab in the No-code
        // pane itself (hiding the canvas), not a split. To open a second block
        // the user returns to the canvas tab first — proving both live in one
        // shared pane, not a Blocks-specific dock.
        const canvasTab = page.locator(".editor-tab").filter({hasText: "No-code"}).first()

        await page.locator("[data-block-id='middle_task']").click()
        await expect(page.getByRole("tab", {name: /middle_task/})).toBeVisible()

        await canvasTab.click()
        await page.locator("[data-block-id='last_task']").click()

        const editorTabs = page.locator(".editor-tabs .editor-tab")
        await expect(editorTabs.filter({hasText: "middle_task"})).toHaveCount(1)
        await expect(editorTabs.filter({hasText: "last_task"})).toHaveCount(1)
    })

    test("the card's open-in-split button opens the task beside the canvas, both visible at once", async ({page}) => {
        // The card button routes straight into a split via MultiPanelTabs, so the
        // canvas and the task edit render simultaneously — impossible if the task
        // had opened as a same-place tab (which hides the canvas).
        const card = page.locator("[data-block-id='middle_task']")
        await card.hover()
        const splitButton = card.locator("[data-test='block-card-open-split']")
        await expect(splitButton).toBeVisible()
        await splitButton.dispatchEvent("click")

        await expect(page.locator("[data-test='block-editor-task-edit']")).toBeVisible()
        await expect(page.locator("[data-test='block-editor-canvas']")).toBeVisible()
    })

    test("the command menu jumps between sections", async ({page}) => {
        await page.keyboard.press("ControlOrMeta+Shift+P")
        const menuInput = page.getByPlaceholder("Type a command or search a task…")
        await expect(menuInput).toBeFocused()

        await menuInput.fill("errors")
        // "errors" also matches "Insert Errors" (which now precedes goto in the
        // list) — activate the goto entry itself, like a user clicking it.
        await page.getByText("Go to Errors", {exact: true}).click()

        await expectRing(page, "__section:errors")
    })

    test("Ctrl/Cmd+S saves the draft from the blocks page", async ({page, request, baseURL}) => {
        // Mutate something first so there is a draft to save
        await walkTo(page, "middle_task")
        await page.keyboard.press("d")
        await expect(page.locator("[data-block-id='middle_task_copy']")).toBeVisible()

        await saveFlow(page)
        const source = await fetchFlowSource(request, baseURL!, flowId)
        expect(source).toContain("middle_task_copy")
    })

    test("the canvas order shown matches the persisted order after mixed mutations", async ({page, request, baseURL}) => {
        // duplicate, then reorder the copy up, then delete the original
        await walkTo(page, "last_task")
        await page.keyboard.press("d")
        await expect(page.locator("[data-block-id='last_task_copy']")).toBeVisible()
        await walkTo(page, "last_task_copy")
        await page.keyboard.press("Alt+ArrowUp")
        await page.keyboard.press("Alt+ArrowUp")
        await walkTo(page, "last_task", "down")
        await page.keyboard.press("Backspace")
        await page.keyboard.press("Enter")
        await expect(page.locator("[data-block-id='last_task']")).toBeHidden()

        await saveFlow(page)
        const source = await fetchFlowSource(request, baseURL!, flowId)
        expect(taskIdsInOrder(source)).toEqual(["seq_group", "last_task_copy", "middle_task"])

        const canvasOrder = (await canvasCardIds(page)).filter(id => !id.startsWith("__"))
        expect(canvasOrder.slice(-2)).toEqual(["last_task_copy", "middle_task"])
    })
})
