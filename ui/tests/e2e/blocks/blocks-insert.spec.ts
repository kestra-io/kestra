import {expect, test} from "@playwright/test"
import {FlowsApi} from "../api/flows.api"
import {canvasCardIds, expectRing, fetchFlowSource, login, openBlockEditor, pickTask, saveFlow, taskIdsInOrder, waitForRing, walkTo} from "./blocks.helpers"

// Every insertion path of the block editor, all keyboard-driven, each verified
// against the YAML the backend actually persisted (not just the DOM).
test.describe("Block editor — insertions", () => {
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

    test("a inserts a task right after the focused block", async ({page, request, baseURL}) => {
        await walkTo(page, "middle_task")
        await page.keyboard.press("a")
        await pickTask(page, "fail", "Fail")

        // Focus lands on the created block, positioned between middle and last
        const newId = await waitForRing(page)

        await saveFlow(page)
        const source = await fetchFlowSource(request, baseURL!, flowId)
        expect(taskIdsInOrder(source)).toEqual(["seq_group", "middle_task", newId, "last_task"])
    })

    test("Shift+A inserts a task right before the focused block", async ({page, request, baseURL}) => {
        await walkTo(page, "middle_task")
        await page.keyboard.press("Shift+A")
        await pickTask(page, "fail", "Fail")

        const newId = await waitForRing(page)

        await saveFlow(page)
        const source = await fetchFlowSource(request, baseURL!, flowId)
        expect(taskIdsInOrder(source)).toEqual(["seq_group", newId, "middle_task", "last_task"])
    })

    test("/ opens the command menu where typing a kind surfaces its insert command", async ({page}) => {
        await walkTo(page, "last_task")
        await page.keyboard.press("/")

        const menuInput = page.getByPlaceholder("Type a command or search a task…")
        await expect(menuInput).toBeFocused()
        await menuInput.fill("trigger")
        await expect(page.locator("[data-test='block-command-menu']").getByText("Insert Triggers", {exact: false})).toBeVisible()
        await page.keyboard.press("Escape")
    })

    test("command-menu Insert <kind> offers every section and opens the picker on that section", async ({page}) => {
        await page.keyboard.press("ControlOrMeta+Shift+P")
        const menu = page.locator("[data-test='block-command-menu']")
        await expect(menu).toBeVisible()

        for (const kind of ["Insert Triggers", "Insert Tasks", "Insert Errors", "Insert Finally"]) {
            await expect(menu.getByText(kind, {exact: false})).toBeVisible()
        }

        await menu.getByText("Insert Errors", {exact: false}).click()
        await expect(page.getByText("Inserting into Errors", {exact: true})).toBeVisible()
        await page.keyboard.press("Escape")

        await page.keyboard.press("ControlOrMeta+Shift+P")
        await menu.getByText("Insert Finally", {exact: false}).click()
        await expect(page.getByText("Inserting into Finally", {exact: true})).toBeVisible()
    })

    test("inserts the first task into an empty top-level section", async ({page, request, baseURL}) => {
        await walkTo(page, "__section:errors")
        await page.keyboard.press("a")
        await expect(page.getByText("Inserting into Errors", {exact: true})).toBeVisible()
        // Fail has no required fields, unlike Log's mandatory "message" — the
        // freshly-inserted block must be saveable with no further editing.
        await pickTask(page, "fail", "Fail")

        await saveFlow(page)
        const source = await fetchFlowSource(request, baseURL!, flowId)
        expect(source).toMatch(/^errors:/m)
    })

    test("inserts the first task into a flowable's own empty lane", async ({page, request, baseURL}) => {
        await walkTo(page, "__lane:tasks[0].errors")
        await page.keyboard.press("a")
        await expect(page.getByText("Inserting into Errors", {exact: true})).toBeVisible()
        await pickTask(page, "fail", "Fail")

        await saveFlow(page)
        const source = await fetchFlowSource(request, baseURL!, flowId)
        // seq_group now carries its own errors lane (indented, not flow-level)
        expect(source).toMatch(/^ {4}errors:/m)
    })

    test("adds a flowable task and steps into its empty branches", async ({page}) => {
        await walkTo(page, "last_task")
        await page.keyboard.press("a")
        await pickTask(page, "if", "If")

        await waitForRing(page)

        // The new If renders as a cluster whose empty "then" lane is reachable
        await page.keyboard.press("ArrowRight")
        await expectRing(page, "__lane:tasks[3].then")
    })

    test("a on a focused trigger offers trigger types, not task types", async ({page}) => {
        // Every trigger type requires config the minimal-insert can't guess
        // (e.g. Webhook's "key"), so this checks placement on the canvas
        // rather than round-tripping through a save that would 422.
        await walkTo(page, "schedule_trigger")
        await page.keyboard.press("a")
        await expect(page.getByText("Inserting into Triggers", {exact: true})).toBeVisible()
        await pickTask(page, "webhook", "Webhook")

        const newId = await waitForRing(page)
        const order = await canvasCardIds(page)
        // ...landed right after the existing trigger and before the first
        // real task, i.e. inside the triggers array, not tasks
        expect(order.slice(0, 3)).toEqual(["schedule_trigger", newId, "seq_group"])
    })

    test("inserts from the command menu, scoped to the focused block", async ({page}) => {
        await walkTo(page, "middle_task")
        await page.keyboard.press("ControlOrMeta+Shift+P")

        const menuInput = page.getByPlaceholder("Type a command or search a task…")
        await expect(menuInput).toBeFocused()
        await expect(page.getByText("context: middle_task")).toBeVisible()
        await expect(page.getByText("Insert task after middle_task")).toBeVisible()

        await page.keyboard.press("Enter")
        await expect(page.getByText("Inserting into Tasks", {exact: true})).toBeVisible()
        await page.keyboard.press("Escape")
    })
})
