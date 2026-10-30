import {expect, test} from "./blocks.fixture"
import {FlowsApi} from "../api/flows.api"
import {expectRing, fetchFlowSource, login, openBlockEditor, pickTask, replaceMonacoContent, saveFlow, sectionTaskIds, walkTo, waitForMonacoStable, waitForRing} from "./blocks.helpers"

// The afterExecution canvas section: same first-class treatment as
// errors/finally — keyboard navigation, insertion, editing, duplication,
// deletion — every mutation verified against the YAML the backend persisted.
test.describe("Block editor — afterExecution section", () => {
    let flowsApi: FlowsApi
    let flowId: string

    test.beforeEach(async ({page, request, baseURL}) => {
        flowsApi = new FlowsApi(request, baseURL)
        flowId = await flowsApi.generateFlowViaApi("blocks-flow-spec.yaml", "blocks-flow-spec-fixture")
        await login(page)
        await openBlockEditor(page, flowId)
    })

    test.afterEach(async () => {
        await flowsApi.removeFlowsViaApi()
    })

    test("renders the section with its task card after Finally", async ({page}) => {
        const section = page.locator("[data-test='block-section-afterExecution']")
        await expect(section).toBeVisible()
        await expect(section).toContainText("After Execution")
        await expect(section.locator("[data-block-id='notify_task']")).toBeVisible()

        // Section order on the canvas mirrors execution semantics
        const sections = await page.locator("[data-test^='block-section-']")
            .evaluateAll(els => els.map(el => el.getAttribute("data-test")))
        expect(sections.indexOf("block-section-afterExecution"))
            .toBeGreaterThan(sections.indexOf("block-section-finally"))
    })

    test("keyboard walk reaches the afterExecution card", async ({page}) => {
        await page.keyboard.press("ArrowDown")
        await walkTo(page, "notify_task")
        await expectRing(page, "notify_task")
    })

    test("a on the afterExecution card inserts a sibling into the section", async ({page, request, baseURL}) => {
        await walkTo(page, "notify_task")
        await page.keyboard.press("a")
        // Fail is valid without any required property, so the flow stays saveable
        await pickTask(page, "fail", "Fail")
        const inserted = await waitForRing(page)

        await saveFlow(page)
        const source = await fetchFlowSource(request, baseURL!, flowId)
        expect(sectionTaskIds(source, "afterExecution")).toEqual(["notify_task", inserted])
        // Siblings in other sections are untouched
        expect(sectionTaskIds(source, "tasks")).toEqual(["main_task"])
        expect(sectionTaskIds(source, "finally")).toEqual(["cleanup_task"])
    })

    test("the section add button opens the picker and the first insert lands in afterExecution", async ({page, request, baseURL}) => {
        const section = page.locator("[data-test='block-section-afterExecution']")
        await section.locator(".block-section-add").click()
        await pickTask(page, "fail", "Fail")
        const inserted = await waitForRing(page)

        await saveFlow(page)
        const source = await fetchFlowSource(request, baseURL!, flowId)
        expect(sectionTaskIds(source, "afterExecution")).toEqual(["notify_task", inserted])
    })

    test("opening the card edits the task under an afterExecution-labelled tab and persists the edit", async ({page, request, baseURL}) => {
        await page.locator("[data-test='block-section-afterExecution'] [data-block-id='notify_task']").click()

        const dock = page.locator("[data-test='block-editor-task-edit']")
        await expect(dock).toBeVisible()
        await expect(page.getByRole("tab", {name: /afterExecution \/ notify_task/})).toBeVisible()

        await waitForMonacoStable(page)
        // Same stable handle as blocks-edit-forms: the Form pane's field order
        // for a Log task is id (0), message (1).
        await replaceMonacoContent(page,
            page.locator("[data-test='block-editor-task-edit'] .task-edit-col-params .monaco-editor:visible").nth(1),
            "notification sent")

        await saveFlow(page)
        const source = await fetchFlowSource(request, baseURL!, flowId)
        expect(source).toContain("message: notification sent")
    })

    test("d duplicates and Backspace deletes inside the section, order persisted", async ({page, request, baseURL}) => {
        await walkTo(page, "notify_task")
        await page.keyboard.press("d")
        await expect(page.locator("[data-block-id='notify_task_copy']")).toBeVisible()

        await saveFlow(page)
        let source = await fetchFlowSource(request, baseURL!, flowId)
        expect(sectionTaskIds(source, "afterExecution")).toEqual(["notify_task", "notify_task_copy"])

        await walkTo(page, "notify_task_copy")
        await page.keyboard.press("Backspace")
        const dialog = page.locator(".kel-message-box")
        await expect(dialog).toBeVisible()
        await page.keyboard.press("Enter")
        await expect(page.locator("[data-block-id='notify_task_copy']")).toBeHidden()

        await saveFlow(page)
        source = await fetchFlowSource(request, baseURL!, flowId)
        expect(sectionTaskIds(source, "afterExecution")).toEqual(["notify_task"])
    })

    test("the command menu jumps to the After Execution section", async ({page}) => {
        await page.keyboard.press("ArrowDown")
        await page.keyboard.press("ControlOrMeta+Shift+p")
        const menu = page.locator("[data-test='block-command-menu']")
        await expect(menu).toBeVisible()
        await menu.getByRole("textbox").fill("After Execution")
        await menu.getByText("Go to After Execution", {exact: true}).click()
        await expectRing(page, "notify_task")
    })
})
