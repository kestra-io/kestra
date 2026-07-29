import {expect, test, type Page} from "@playwright/test"
import {FlowsApi} from "../api/flows.api"
import {backToCanvas, fetchFlowSource, login, openBlockEditor, replaceMonacoContent, saveFlow, walkTo, waitForMonacoStable} from "./blocks.helpers"

// Editing a task through the dock: every input family of the generated form
// (text via the inline Monaco fields, enum select, segmented String/Array,
// boolean switch, duration presets) plus the raw Source tab — each mutation
// verified against the persisted YAML.
test.describe("Block editor — form editing", () => {
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

    // Opening a block hands off to the flow editor's shared dock (see
    // MERGE-PLAN.md) — its pane shows exactly one active tab's TaskEdit at a
    // time here (no split), so scoping by data-test is enough; there is no
    // per-tab data-dock-pane-id attribute anymore.
    async function openDock(page: Page, id: string) {
        await walkTo(page, id)
        await page.keyboard.press("Enter")
        await expect(page.getByRole("tab", {name: new RegExp(id)})).toBeVisible()
        await expect(page.locator("[data-test='block-editor-task-edit']")).toBeVisible()
        await waitForMonacoStable(page)
    }

    // The generated form renders its text fields as inline Monaco editors, so
    // they carry no data-test hooks of their own — the Form pane's field order
    // (id, message for a Log task) is the only stable handle.
    function formMonacoField(page: Page, index: number) {
        return page.locator("[data-test='block-editor-task-edit'] .task-edit-col-params .monaco-editor:visible").nth(index)
    }

    test("Source tab keeps comments and exact quoting from Flow Code", async ({page}) => {
        const fid = await flowsApi.generateFlowViaApi("blocks-fidelity.yaml", "blocks-fidelity-fixture")
        await openBlockEditor(page, fid)
        await openDock(page, "commented_task")
        await page.getByRole("tab", {name: "Source"}).click()

        const dock = page.locator("[data-test='block-editor-task-edit']")
        await expect(dock).toContainText("survive")
        await expect(dock).toContainText("«")
        await expect(dock).toContainText("bonjour")
    })

    test("renames the task id from the form and the canvas card follows", async ({page, request, baseURL}) => {
        await openDock(page, "middle_task")

        await replaceMonacoContent(page, formMonacoField(page, 0), "renamed_task")

        // The canvas sits behind its own tab while the task is open — coming
        // back to it shows the renamed card
        await backToCanvas(page)
        await expect(page.locator("[data-block-id='renamed_task']")).toBeVisible()

        await saveFlow(page)
        const source = await fetchFlowSource(request, baseURL!, flowId)
        expect(source).toContain("id: renamed_task")
        expect(source).not.toContain("id: middle_task")
    })

    test("edits the message field and persists it", async ({page, request, baseURL}) => {
        await openDock(page, "middle_task")

        await replaceMonacoContent(page, formMonacoField(page, 1), "changed by e2e")

        await saveFlow(page)
        const source = await fetchFlowSource(request, baseURL!, flowId)
        expect(source).toContain("changed by e2e")
    })

    test("switches the message between String and Array with the segmented control", async ({page}) => {
        await openDock(page, "middle_task")
        const pane = page.locator("[data-test='block-editor-task-edit']")

        // The radio input itself is a zero-size a11y node — its .kel-segmented
        // wrapper label is the visible, clickable surface.
        await pane.getByRole("radio", {name: "Array"}).locator("..").click()
        await expect(pane.getByRole("button", {name: "+ Add to message"}).first()).toBeVisible()

        await pane.getByRole("radio", {name: "String"}).locator("..").click()
        await expect(pane.getByRole("radio", {name: "String"})).toBeChecked()
    })

    test("selects an enum value and toggles a boolean switch, both persisted", async ({page, request, baseURL}) => {
        await openDock(page, "middle_task")
        const pane = page.locator("[data-test='block-editor-task-edit']")
        // Scoped to the Form column — the Inputs column has its own "Execution
        // context" section, whose accessible name also matches /Execution/.
        const form = pane.locator(".task-edit-col-params")

        // Enum: the Log task's "level" select, inside the collapsed Logging group
        await form.getByRole("button", {name: /Logging/}).click()
        await form.locator(".kel-select").filter({hasText: "INFO"}).first().click()
        await page.getByRole("option", {name: "DEBUG"}).click()

        // Boolean: the task-level "disabled" switch, inside the Execution group.
        // The role=switch input itself is a zero-size a11y node — the visible,
        // clickable surface is its .kel-switch wrapper.
        await form.getByRole("button", {name: /Execution/}).click()
        await form.getByRole("switch", {name: /disabled/}).locator("..").click()

        await saveFlow(page)
        const source = await fetchFlowSource(request, baseURL!, flowId)
        expect(source).toContain("level: DEBUG")
        expect(source).toMatch(/disabled: true/)
    })

    test("fills a duration field from its preset buttons", async ({page, request, baseURL}) => {
        await openDock(page, "middle_task")
        const pane = page.locator("[data-test='block-editor-task-edit']")
        const form = pane.locator(".task-edit-col-params")

        await form.getByRole("button", {name: /Execution/}).click()
        await form.getByRole("button", {name: "30s", exact: true}).first().click()

        await saveFlow(page)
        const source = await fetchFlowSource(request, baseURL!, flowId)
        expect(source).toContain("PT30S")
    })

    test("edits raw YAML in the Source tab and the canvas syncs", async ({page, request, baseURL}) => {
        await openDock(page, "last_task")
        const pane = page.locator("[data-test='block-editor-task-edit']")

        await pane.getByText("Source", {exact: true}).click()
        await waitForMonacoStable(page, pane)
        await replaceMonacoContent(page, pane.locator(".task-edit-col-params .monaco-editor:visible").first(),
            "id: source_edited\ntype: io.kestra.plugin.core.log.Log\nmessage: from source tab")

        // The canvas card renames once the debounced sync lands
        await backToCanvas(page)
        await expect(page.locator("[data-block-id='source_edited']")).toBeVisible()

        await saveFlow(page)
        const source = await fetchFlowSource(request, baseURL!, flowId)
        expect(source).toContain("id: source_edited")
        expect(source).toContain("from source tab")
    })

    test("editing one open tab never bleeds into another open tab", async ({page, request, baseURL}) => {
        // Regression: the Source tabs of two open tasks used to share one
        // Monaco model, silently overwriting each other.
        await openDock(page, "middle_task")
        await backToCanvas(page)
        await page.locator("[data-block-id='last_task']").click()
        await expect(page.getByRole("tab", {name: /last_task/})).toBeVisible()

        const lastPane = page.locator("[data-test='block-editor-task-edit']")
        await lastPane.getByText("Source", {exact: true}).click()
        await waitForMonacoStable(page, lastPane)
        await replaceMonacoContent(page, lastPane.locator(".task-edit-col-params .monaco-editor:visible").first(),
            "id: last_task\ntype: io.kestra.plugin.core.log.Log\nmessage: only last changed")

        await saveFlow(page)
        const source = await fetchFlowSource(request, baseURL!, flowId)
        expect(source).toContain("only last changed")
        // middle_task is untouched — both its id and original message survive
        expect(source).toContain("id: middle_task")
        expect(source).toContain("message: middle")
    })

    test("two tasks sharing the same id keep distinct focus rings", async ({page}) => {
        // Craft the collision live from the Source tab
        await openDock(page, "last_task")
        const pane = page.locator("[data-test='block-editor-task-edit']")
        await pane.getByText("Source", {exact: true}).click()
        await waitForMonacoStable(page, pane)
        await replaceMonacoContent(page, pane.locator(".task-edit-col-params .monaco-editor:visible").first(),
            "id: middle_task\ntype: io.kestra.plugin.core.log.Log\nmessage: duplicate id")

        // Both cards render, disambiguated by 0-based tasks[] index (last_task
        // sits at index 2)
        await backToCanvas(page)
        await expect(page.locator("[data-block-id='middle_task#2']")).toBeVisible()
        await expect(page.locator("[data-block-id='middle_task']")).toBeVisible()

        // Walking the ring onto one rings only that one, not the other
        await walkTo(page, "middle_task#2")
        await expect(page.locator(".block-kbd-focused")).toHaveCount(1)
    })
})
