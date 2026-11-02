import {expect, type Locator, type Page, type Request} from "@playwright/test"

const TENANT = process.env.E2E_TENANT ?? "main"

export const FLOW_TAB = "code"

/** The "Desktop Chrome" device sends a Windows user agent, so Monaco uses its Windows key bindings on every host. */
const DOCUMENT_END = "Control+End"

/** No trailing newline, so text typed at the end of the document lands on the `message` line. */
export function flowSource(namespace: string, flowId: string, message: string) {
    return [
        `id: ${flowId}`,
        `namespace: ${namespace}`,
        "",
        "tasks:",
        "  - id: hello",
        "    type: io.kestra.plugin.core.log.Log",
        `    message: ${message}`,
    ].join("\n")
}

export function fileTabId(path: string) {
    return `code-${path}`
}

/**
 * Drives the multi-panel editor shared by the flow editor and the namespace Files page. Tabs are
 * found by their `data-tab-id`, and an editor through the panel its tab sits in, because the other
 * panels keep their own Monaco instances on the page.
 */
export class FilesEditor {
    private pendingFileReads = 0

    constructor(public readonly page: Page) {
        const isFileRead = (request: Request) => request.method() === "GET" && /\/files\?path=/.test(request.url())
        const settled = (request: Request) => {
            if (isFileRead(request)) this.pendingFileReads--
        }
        page.on("request", (request) => {
            if (isFileRead(request)) this.pendingFileReads++
        })
        page.on("requestfinished", settled)
        page.on("requestfailed", settled)
    }

    /**
     * A tab fetches its file twice when it opens (once mounted, once activated), and a response
     * landing after the user typed replaces what they typed, so nothing is typed while a read is in flight.
     */
    private async settleFileReads() {
        await expect.poll(() => this.pendingFileReads, {message: "namespace file reads still in flight"}).toBe(0)
    }

    async gotoFlow(namespace: string, flowId: string) {
        await this.page.goto(`/ui/${TENANT}/flows/edit/${namespace}/${flowId}/edit`)
        await expect(this.tab(FLOW_TAB)).toBeVisible()
        await expect(this.editor(FLOW_TAB)).toContainText(`id: ${flowId}`)
    }

    async gotoNamespaceFiles(namespace: string) {
        await this.page.goto(`/ui/${TENANT}/namespaces/edit/${namespace}/files`)
    }

    tab(uid: string): Locator {
        return this.page.locator(`[role="tab"][data-tab-id="${uid}"]`)
    }

    dirtyMarker(uid: string): Locator {
        return this.tab(uid).locator("[data-test='tab-dirty']")
    }

    /** The content area of the panel holding `uid`: it renders that panel's active tab only. */
    contentPanel(uid: string): Locator {
        return this.tab(uid)
            .locator("xpath=ancestor::*[.//*[contains(concat(' ', @class, ' '), ' content-panel ')]][1]")
            .locator(".content-panel")
    }

    editor(uid: string): Locator {
        return this.contentPanel(uid).getByTestId("monaco-editor")
    }

    async openFilesPanel() {
        if (!(await this.tab("files").isVisible())) {
            await this.page.getByRole("button", {name: "Files", exact: true}).click()
        }
        await expect(this.tab("files")).toBeVisible()
    }

    /** The row's label: a tree item's accessible name also carries its icon's alt text. */
    treeEntry(name: string): Locator {
        return this.page.getByRole("tree").getByText(name, {exact: true})
    }

    async openFileFromTree(name: string, path = name) {
        await this.treeEntry(name).click()
        await this.waitForFileTab(path)
    }

    async openFileFromSearch(path: string) {
        await this.page.getByRole("combobox").click()
        await this.page.keyboard.insertText(path.split("/").pop()!)
        await this.page.getByRole("option", {name: path, exact: true}).click()
        await this.waitForFileTab(path)
    }

    async waitForFileTab(path: string) {
        const uid = fileTabId(path)
        await expect(this.tab(uid)).toHaveClass(/active/)
        await expect(this.editor(uid)).toBeVisible()
        await this.settleFileReads()
    }

    async selectTab(uid: string) {
        await this.tab(uid).click()
        await expect(this.tab(uid)).toHaveClass(/active/)
        await expect(this.editor(uid)).toBeVisible()
        await this.settleFileReads()
    }

    async closeTab(uid: string) {
        await this.tab(uid).locator("[data-test='tab-close']").click()
        await expect(this.tab(uid)).toHaveCount(0)
    }

    /** Waits for `text` so a test never types into a tab whose content is still loading. */
    async expectContent(uid: string, text: string) {
        await expect(this.editor(uid)).toContainText(text)
    }

    /**
     * Puts the caret at the very end of the document and types `text`. Each line goes in as one
     * input and each newline as Escape then Enter, so a word suggestion never swallows the Enter.
     */
    async typeAtEnd(uid: string, text: string) {
        await this.settleFileReads()
        const editor = this.editor(uid)
        await expect(async () => {
            await editor.click()
            await expect(this.contentPanel(uid).locator("textarea.inputarea")).toBeFocused({timeout: 1000})
        }).toPass({timeout: 15000})
        await this.page.keyboard.press(DOCUMENT_END)
        await this.page.keyboard.press("End")
        const [first, ...rest] = text.split("\n")
        await this.page.keyboard.insertText(first)
        for (const line of rest) {
            await this.page.keyboard.press("Escape")
            await this.page.keyboard.press("Enter")
            await this.page.keyboard.insertText(line)
        }
    }

    async replaceContent(uid: string, text: string) {
        await this.settleFileReads()
        const textarea = this.contentPanel(uid).getByTestId("monaco-editor-hidden-synced-textarea")
        await textarea.fill(text, {force: true})
        await textarea.blur()
    }

    /** Control rather than ControlOrMeta: the device's Windows user agent makes it the editor's save chord on every host. */
    async pressSave() {
        await this.page.keyboard.press("Control+s")
    }

    async clickSave() {
        await this.page.getByRole("button", {name: "Save", exact: true}).click()
    }
}
