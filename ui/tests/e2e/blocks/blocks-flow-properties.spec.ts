import {expect, test, type Locator, type Page} from "@playwright/test"
import {FlowsApi} from "../api/flows.api"
import {fetchFlowSource, login, openBlockEditor, replaceMonacoContent, saveFlow, waitForMonacoStable} from "./blocks.helpers"

// The flow properties panel behind the Flow card's Configure button: every
// flow-level field of the spec is editable there, and each edit lands in the
// YAML the backend persists.
test.describe("Block editor — flow properties panel", () => {
    let flowsApi: FlowsApi
    let flowId: string

    test.beforeEach(async ({page, request, baseURL}) => {
        flowsApi = new FlowsApi(request, baseURL)
        flowId = await flowsApi.generateFlowViaApi("blocks-flow-spec.yaml", "blocks-flow-spec-fixture")
        await login(page)
        await openBlockEditor(page, flowId)
        await page.locator("[data-test='block-editor-configure-flow']").click()
        await expect(page.locator("[data-test='flow-properties-edit']")).toBeVisible()
    })

    test.afterEach(async () => {
        await flowsApi.removeFlowsViaApi()
    })

    function panel(page: Page): Locator {
        return page.locator("[data-test='flow-properties-edit']")
    }

    function field(page: Page, label: string): Locator {
        return panel(page).locator(".schema-wrapper, .tasks-wrapper").filter({
            has: page.locator(".label", {hasText: new RegExp(`^${label}$`)}),
        }).first()
    }

    async function closePanelAndSave(page: Page) {
        await page.locator("[data-test='flow-properties-back']").click()
        await saveFlow(page)
    }

    test("exposes every flow-level field of the spec", async ({page}) => {
        for (const key of [
            "id", "namespace", "description", "labels", "variables", "outputs",
            "concurrency", "retry", "sla", "checks", "workerSelector", "disabled",
        ]) {
            await expect(panel(page).getByText(key, {exact: true}).first(), `field ${key}`).toBeVisible()
        }
        // The list-type fields render header-style labels with their count
        await expect(panel(page).getByText(/inputs \(\d+\)/).first()).toBeVisible()
        // pluginDefaults is deliberately NOT offered here: managing plugin
        // defaults belongs to the namespace-level Plugin Defaults surface, not
        // the no-code flow editor.
        await expect(panel(page).getByText(/pluginDefaults \(\d+\)/)).toBeHidden()
        // quotas is deliberately NOT offered: the OSS executor rejects it at
        // runtime (EE feature) in a way that crash-loops the server
        await expect(panel(page).getByText("quotas", {exact: true})).toBeHidden()
    })

    test("id and namespace are locked when editing an existing flow", async ({page}) => {
        // Both render as locked inputs — readonly (white, with a lock icon), not
        // the greyed disabled look — so there is no way to type into them.
        const idField = field(page, "id")
        const nsField = field(page, "namespace")
        await expect(idField.locator("input, .monaco-editor, [class*=disabled]").first()).toBeVisible()
        expect(await idField.locator("input:not([disabled]):not([readonly])").count()).toBe(0)
        expect(await nsField.locator("input:not([disabled]):not([readonly])").count()).toBe(0)
    })

    test("edits the description and persists it", async ({page, request, baseURL}) => {
        await waitForMonacoStable(page)
        await replaceMonacoContent(page, field(page, "description").locator(".monaco-editor:visible").first(), "documented by e2e")

        await closePanelAndSave(page)
        const source = await fetchFlowSource(request, baseURL!, flowId)
        expect(source).toContain("description: documented by e2e")
    })

    test("adds a variable through + Add to variables and persists it", async ({page, request, baseURL}) => {
        await panel(page).getByRole("button", {name: "+ Add to variables"}).click()
        const variables = field(page, "variables")
        await variables.getByPlaceholder("Key").fill("env")
        await waitForMonacoStable(page)
        await replaceMonacoContent(page, variables.locator(".monaco-editor:visible").first(), "prod")
        // TaskDict batches its update behind a 200ms debounce — let it flush
        // before the panel closes, as a user pausing before clicking Back would
        await page.waitForTimeout(500)

        await closePanelAndSave(page)
        const source = await fetchFlowSource(request, baseURL!, flowId)
        expect(source).toContain("variables:")
        expect(source).toContain("env: prod")
    })

    test("sets the concurrency limit and persists it", async ({page, request, baseURL}) => {
        const concurrency = panel(page).locator(".nested-card").filter({hasText: "concurrency"})
        const limit = concurrency.getByRole("spinbutton").first()
        await limit.fill("3")
        await limit.blur()

        await closePanelAndSave(page)
        const source = await fetchFlowSource(request, baseURL!, flowId)
        expect(source).toContain("concurrency:")
        expect(source).toContain("limit: 3")
    })

    test("picks a retry variant inside its contained card and clears it again", async ({page, request, baseURL}) => {
        const retry = panel(page).locator(".nested-card").filter({hasText: "retry"}).first()
        await expect(retry).toBeVisible()

        // The variant chips live INSIDE the card (containment), and selecting
        // one surfaces its fields plus the clear affordance in the card head
        await retry.getByText("Constant", {exact: true}).first().click()
        await expect(retry.getByText("interval").first()).toBeVisible()

        await retry.getByRole("button", {name: "30s", exact: true}).first().click()
        await closePanelAndSave(page)
        let source = await fetchFlowSource(request, baseURL!, flowId)
        expect(source).toContain("retry:")
        expect(source).toContain("PT30S")

        // Clear selection removes the whole retry block from the YAML
        await page.locator("[data-test='block-editor-configure-flow']").click()
        await panel(page).locator(".nested-card").filter({hasText: "retry"}).first()
            .getByText("Clear selection").click()
        await closePanelAndSave(page)
        source = await fetchFlowSource(request, baseURL!, flowId)
        expect(source).not.toContain("retry:")
    })

    test("toggles disabled from the panel, with its help tooltip, and persists it", async ({page, request, baseURL}) => {
        const disabled = field(page, "disabled")
        // The kill switch explains itself on hover
        await disabled.locator(".information-icon, .material-design-icon").first().hover()
        await expect(page.getByText("A disabled flow does not run", {exact: false}).first()).toBeVisible()

        await disabled.locator(".kel-switch").first().click()

        await closePanelAndSave(page)
        const source = await fetchFlowSource(request, baseURL!, flowId)
        expect(source).toContain("disabled: true")
    })

    test("every empty list field names its own add target", async ({page}) => {
        for (const key of ["variables", "outputs", "sla", "checks"]) {
            await expect(panel(page).getByRole("button", {name: `+ Add to ${key}`}), `add button for ${key}`).toBeVisible()
        }
    })
})
