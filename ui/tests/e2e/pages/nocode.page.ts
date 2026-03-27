import type {Page, Locator} from "@playwright/test";
import {expect} from "@playwright/test";
import {BasePage} from "./base.page";
import {shared} from "../fixtures/shared";

/**
 * Page Object Model for the No-Code flow editor.
 *
 * The no-code editor is one panel in the multi-panel flow editor view.
 * It coexists with the Flow Code (Monaco YAML) panel.
 * Both panels share the same flow YAML state.
 *
 * Typical flow:
 * 1. Login and navigate to /ui/main/flows/edit/:namespace/:id/edit
 * 2. Open the no-code panel (it may already be open if default is NO_CODE)
 * 3. Interact with tasks/triggers/pluginDefaults sections
 * 4. Verify YAML changes in the flow code panel
 * 5. Save the flow
 */
export class NoCodePage extends BasePage {
    constructor(public readonly page: Page) {
        super(page);
    }

    /** Login and navigate to the flow editor (edit tab). */
    async gotoFlowEdit(namespace: string, flowId: string): Promise<void> {
        await this.login();
        await this.page.goto(`/ui/main/flows/edit/${namespace}/${flowId}/edit`);
        await this.page.waitForURL("**/flows/edit/**");
    }

    // ─── Panel management ────────────────────────────────────────────────

    /**
     * Open the No-Code panel by clicking its tab button.
     * If already open, this is a no-op (clicking toggles it, so we check first).
     */
    async openNoCodePanel(): Promise<void> {
        const btn = this.page.getByRole("button", {name: "No-code"});
        const isActive = await btn.evaluate((el) => el.classList.contains("active"));
        if (!isActive) {
            await btn.click();
        }
        await expect(this.page.locator(".no-code").first()).toBeVisible();
    }

    /**
     * Open the Flow Code (Monaco) panel by clicking its tab button.
     * Keeps any other open panels open alongside it.
     */
    async openCodePanel(): Promise<void> {
        const btn = this.page.getByRole("button", {name: "Flow Code"});
        const isActive = await btn.evaluate((el) => el.classList.contains("active"));
        if (!isActive) {
            await btn.click();
        }
    }

    // ─── No-code section interactions ────────────────────────────────────

    /**
     * Click the "Add" button in a named collapsible section of the no-code panel.
     * Sections: "tasks", "triggers", "pluginDefaults".
     *
     * The section header contains both the section title (e.g. "tasks (0)")
     * and the "Add" button rendered in the El Collapse icon slot.
     */
    async clickAddInSection(sectionName: string): Promise<void> {
        const header = this.page
            .locator(".el-collapse-item__header")
            .filter({hasText: new RegExp(sectionName, "i")})
            .first();
        await header.getByRole("button", {name: "Add"}).click();
    }

    /**
     * Select a plugin type in the PluginSelect (el-select, filterable) dropdown.
     *
     * After clicking "Add" in a section, a creation tab opens showing a PluginSelect
     * for choosing the task/trigger type. This method handles the el-select interaction:
     * click → type filter → pick option.
     */
    async selectPluginType(pluginClass: string): Promise<void> {
        // The PluginSelect is the el-select for the type field. In the task creation
        // tab it is the first (and only) visible el-select in the form.
        const select = this.page.locator(".el-select").filter({
            has: this.page.locator(".el-select__placeholder"),
        }).first();
        await select.click();

        // Type into the filterable input to narrow options
        const filterInput = this.page.locator(".el-select__input").first();
        await filterInput.fill(pluginClass);

        // Wait for dropdown to show filtered results
        await this.page.waitForSelector(".el-select-dropdown__item:visible", {timeout: 5000});

        // Click the matching option
        await this.page
            .locator(".el-select-dropdown__item")
            .filter({hasText: pluginClass})
            .first()
            .click();

        // Wait a moment for the form to render after type selection
        await this.page.waitForTimeout(500);
    }

    /**
     * Fill a form field in the no-code task editor by label name.
     *
     * String fields use a Monaco editor in "input" mode. We interact via the
     * hidden synced textarea that is always present inside MonacoEditor.vue.
     *
     * Other simple fields (number, boolean) use standard Element Plus inputs.
     */
    async fillField(fieldLabel: string, value: string): Promise<void> {
        // Scope to the form item whose label span exactly matches the field name
        const formItem = this.page
            .locator(".el-form-item")
            .filter({
                has: this.page.locator("span.label").filter({hasText: new RegExp(`^\\s*${fieldLabel}\\s*$`)}),
            })
            .first();

        const monacoTextarea = formItem.getByTestId("monaco-editor-hidden-synced-textarea");
        const regularInput = formItem.locator("input.el-input__inner");

        if (await monacoTextarea.count() > 0) {
            await monacoTextarea.clear({force: true});
            await monacoTextarea.fill(value, {force: true});
        } else if (await regularInput.count() > 0) {
            await regularInput.clear();
            await regularInput.fill(value);
        }

        // Allow debounce to propagate the change
        await this.page.waitForTimeout(600);
    }

    // ─── Element list interactions ────────────────────────────────────────

    /**
     * Click a task/trigger/pluginDefault element in the list to open its edit tab.
     * Elements are identified by their displayed id or type text.
     */
    async clickElement(idOrName: string): Promise<void> {
        await this.page
            .locator(".element")
            .filter({hasText: idOrName})
            .first()
            .click();
    }

    /**
     * Delete an element from the list by clicking its trash icon button.
     */
    async deleteElement(idOrName: string): Promise<void> {
        await this.page
            .locator(".element")
            .filter({hasText: idOrName})
            .first()
            .locator("button.delete-element")
            .click();
        // Wait for debounce
        await this.page.waitForTimeout(600);
    }

    // ─── YAML verification ────────────────────────────────────────────────

    /**
     * Read the current flow YAML from the Monaco editor's hidden synced textarea.
     *
     * The flow YAML editor is inside #editorWrapper. We scope the query to avoid
     * picking up Monaco instances from task field editors in the no-code panel.
     */
    async getFlowYaml(): Promise<string> {
        // If the flow code panel is not open, we use any visible Monaco hidden textarea
        // that corresponds to the flow YAML (it's always rendered in the background).
        const flowEditorTextarea = this.page
            .locator("#editorWrapper")
            .getByTestId("monaco-editor-hidden-synced-textarea");

        if (await flowEditorTextarea.count() > 0) {
            return flowEditorTextarea.inputValue();
        }

        // Fallback: the first hidden textarea should be the flow YAML
        return this.page
            .getByTestId("monaco-editor-hidden-synced-textarea")
            .first()
            .inputValue();
    }

    /**
     * Wait for the flow YAML to contain a given substring (with retries for debounce).
     */
    async expectYamlContains(text: string): Promise<void> {
        await expect.poll(
            async () => this.getFlowYaml(),
            {timeout: 5000, message: `Expected YAML to contain: "${text}"`},
        ).toContain(text);
    }

    /**
     * Assert the flow YAML does NOT contain a given substring.
     */
    async expectYamlNotContains(text: string): Promise<void> {
        await expect.poll(
            async () => this.getFlowYaml(),
            {timeout: 5000, message: `Expected YAML NOT to contain: "${text}"`},
        ).not.toContain(text);
    }

    /**
     * Set the flow YAML directly via the Monaco editor (requires the code panel to be open).
     */
    async setFlowYaml(yaml: string): Promise<void> {
        await this.openCodePanel();
        const flowEditorTextarea = this.page
            .locator("#editorWrapper")
            .getByTestId("monaco-editor-hidden-synced-textarea");
        await flowEditorTextarea.clear({force: true});
        await flowEditorTextarea.fill(yaml, {force: true});
        // Allow the no-code panel to sync
        await this.page.waitForTimeout(1500);
    }

    // ─── Save ─────────────────────────────────────────────────────────────

    /**
     * Click the "Save" button and assert the "Successfully saved" success toast.
     */
    async saveFlow(): Promise<void> {
        await this.page.getByRole("button", {name: "Save"}).first().click();
        await expect(
            this.page.getByRole("heading", {name: "Successfully saved"}),
        ).toBeVisible({timeout: 10000});
    }

    // ─── Compound helpers ──────────────────────────────────────────────────

    /**
     * Full workflow: add a task/trigger/pluginDefault entry in a section,
     * select its type, optionally fill the id field, then wait for YAML sync.
     *
     * Returns immediately after type selection; use fillField() for extra fields.
     */
    async addEntry(section: string, pluginClass: string, id?: string): Promise<void> {
        await this.clickAddInSection(section);
        await this.selectPluginType(pluginClass);
        if (id) {
            await this.fillField("id", id);
        }
        // Wait for no-code → YAML debounce
        await this.page.waitForTimeout(1200);
    }
}
