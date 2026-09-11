import {expect, test} from "./fixtures/auth"
import type {Locator, Page} from "@playwright/test"
import {v4 as uuidv4} from "uuid"

// The repo doesn't set `testIdAttribute`, so `data-test` is matched by hand.
const CREATE = "[data-test=\"flows-create\"]"
const TENANT = process.env.E2E_TENANT ?? "main"
const NAMESPACE = "company.team"

const source = (id: string) => [
    `id: ${id}`,
    `namespace: ${NAMESPACE}`,
    "",
    "tasks:",
    "  - id: hello",
    "    type: io.kestra.plugin.core.log.Log",
    "    message: hello",
    "",
].join("\n")

// `id` and `namespace` are immutable once a flow exists. The no-code panel has
// always greyed them out; these cover the same rule in the YAML editor, where
// the edit used to be accepted and then undone a second later (#17615).
test.describe("Flow editor — immutable id and namespace", () => {
    // Real flows against the one shared Kestra instance, so keep them in a single
    // worker. `default` rather than `serial` so a failure doesn't mask the rest.
    test.describe.configure({mode: "default"})

    let flowId = ""

    test.beforeEach(() => {
        flowId = `readonly_${uuidv4().replace(/-/g, "_")}`.slice(0, 24)
    })

    function editorText(page: Page): Locator {
        return page.getByTestId("monaco-editor").first()
    }

    /**
     * Put the caret at the end of a rendered editor line and type.
     *
     * Clicking the line is what makes this a real user interaction — Ctrl/Cmd+Home
     * is not bound to "document start" in Monaco on macOS, so navigating by
     * keyboard silently leaves the caret wherever it already was.
     */
    async function typeOnLine(page: Page, lineIndex: number, text: string) {
        const line = page.locator(".monaco-editor .view-lines .view-line").nth(lineIndex)
        await expect(line).toBeVisible()
        await expect(async () => {
            await line.click()
            await expect(page.locator(".monaco-editor textarea.inputarea").first()).toBeFocused({timeout: 1000})
        }).toPass({timeout: 15000})
        await page.keyboard.press("End")
        await page.keyboard.type(text)
    }

    /**
     * One-shot read, deliberately not a retrying assertion.
     *
     * The behaviour being replaced also ends up without the typed text — it let the
     * edit land and stripped it on the next onEdit tick — so anything that retries
     * passes either way. What distinguishes the lock is that the character is never
     * inserted in the first place.
     */
    async function documentNow(page: Page): Promise<string> {
        return (await editorText(page).textContent()) ?? ""
    }

    async function seedEditor(page: Page) {
        await page.goto("/ui/flows")
        await expect(page.locator(CREATE)).toBeVisible()
        await page.locator(CREATE).click()
        await page.waitForURL("**/flows/new")

        const textarea = page.getByTestId("monaco-editor-hidden-synced-textarea")
        await textarea.clear({force: true})
        await textarea.fill(source(flowId), {force: true})
        await textarea.blur()
        await expect(editorText(page)).toContainText(flowId)
    }

    async function createFlow(page: Page) {
        await seedEditor(page)

        await page.getByRole("button", {name: "Save", exact: true}).click()
        await expect(page.getByRole("heading", {name: "Successfully saved"})).toBeVisible()

        // Land on the saved flow's editor explicitly, so the store is in the
        // "existing flow" state rather than still mid-creation.
        await page.goto(`/ui/${TENANT}/flows/edit/${NAMESPACE}/${flowId}/edit`)
        await expect(editorText(page)).toContainText(flowId)
    }

    test("refuses a keystroke aimed at the id line", async ({page}) => {
        await createFlow(page)

        await typeOnLine(page, 0, "XXX")

        // Never inserted — not inserted and then withdrawn.
        expect(await documentNow(page)).not.toContain("XXX")
        // Still absent once the old debounce window has elapsed.
        await page.waitForTimeout(1500)
        expect(await documentNow(page)).not.toContain("XXX")
        await expect(editorText(page)).toContainText(`id: ${flowId}`)
    })

    test("refuses a keystroke aimed at the namespace line", async ({page}) => {
        await createFlow(page)

        await typeOnLine(page, 1, "ZZZ")

        expect(await documentNow(page)).not.toContain("ZZZ")
        await page.waitForTimeout(1500)
        expect(await documentNow(page)).not.toContain("ZZZ")
        await expect(editorText(page)).toContainText(`namespace: ${NAMESPACE}`)
    })

    test("still accepts edits to the rest of the document", async ({page}) => {
        await createFlow(page)

        // The `message: hello` line, which carries no lock.
        await typeOnLine(page, 6, "_edited")

        await expect(editorText(page)).toContainText("message: hello_edited")
        await expect(editorText(page)).toContainText(`id: ${flowId}`)
    })

    test("leaves both fields editable while creating a flow", async ({page}) => {
        await seedEditor(page)

        await typeOnLine(page, 0, "_edited")

        // Nothing is saved yet, so there is no immutable value to protect.
        await expect(editorText(page)).toContainText(`id: ${flowId}_edited`)
    })
})
