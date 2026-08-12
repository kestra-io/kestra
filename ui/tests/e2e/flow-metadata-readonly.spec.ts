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

    function editor(page: Page): Locator {
        return page.getByTestId("monaco-editor").first()
    }

    // Click and PROVE the caret landed before typing — the editor shell's async
    // side effects can steal focus between the click and the first keystroke.
    async function focusEditor(page: Page) {
        const target = editor(page)
        await expect(async () => {
            await target.click()
            await expect(target.locator("textarea.inputarea")).toBeFocused({timeout: 1000})
        }).toPass({timeout: 15000})
    }

    async function createFlow(page: Page) {
        await page.goto("/ui/flows")
        await expect(page.locator(CREATE)).toBeVisible()
        await page.locator(CREATE).click()
        await page.waitForURL("**/flows/new")

        const textarea = page.getByTestId("monaco-editor-hidden-synced-textarea")
        await textarea.clear({force: true})
        await textarea.fill(source(flowId), {force: true})
        await textarea.blur()
        await expect(editor(page).getByText(flowId)).toBeVisible()

        await page.getByRole("button", {name: "Save", exact: true}).click()
        await expect(page.getByRole("heading", {name: "Successfully saved"})).toBeVisible()

        // Land on the saved flow's editor explicitly, so the store is in the
        // "existing flow" state rather than still mid-creation.
        await page.goto(`/ui/${TENANT}/flows/edit/${NAMESPACE}/${flowId}/edit`)
        await expect(editor(page).getByText(flowId).first()).toBeVisible()
    }

    test("refuses a keystroke aimed at the id line", async ({page}) => {
        await createFlow(page)
        await focusEditor(page)

        // Caret to the end of line 1, which is `id: <flowId>`.
        await page.keyboard.press("ControlOrMeta+Home")
        await page.keyboard.press("End")
        await page.keyboard.type("XXX")

        // The character never lands: no toast, no revert a second later.
        await expect(editor(page)).not.toContainText(`${flowId}XXX`)
        await expect(editor(page).getByText(flowId).first()).toBeVisible()
    })

    test("refuses a keystroke aimed at the namespace line", async ({page}) => {
        await createFlow(page)
        await focusEditor(page)

        await page.keyboard.press("ControlOrMeta+Home")
        await page.keyboard.press("ArrowDown")
        await page.keyboard.press("End")
        await page.keyboard.type(".zzz")

        await expect(editor(page)).not.toContainText(`${NAMESPACE}.zzz`)
        await expect(editor(page)).toContainText(NAMESPACE)
    })

    test("still accepts edits to the rest of the document", async ({page}) => {
        await createFlow(page)
        await focusEditor(page)

        await page.keyboard.press("ControlOrMeta+End")
        await page.keyboard.type("# edited by e2e")

        await expect(editor(page)).toContainText("# edited by e2e")
    })

    test("leaves both fields editable while creating a flow", async ({page}) => {
        await page.goto("/ui/flows")
        await expect(page.locator(CREATE)).toBeVisible()
        await page.locator(CREATE).click()
        await page.waitForURL("**/flows/new")

        const textarea = page.getByTestId("monaco-editor-hidden-synced-textarea")
        await textarea.clear({force: true})
        await textarea.fill(source(flowId), {force: true})
        await textarea.blur()
        await expect(editor(page).getByText(flowId)).toBeVisible()

        await focusEditor(page)
        await page.keyboard.press("ControlOrMeta+Home")
        await page.keyboard.press("End")
        await page.keyboard.type("_edited")

        // Nothing is saved yet, so there is no immutable value to protect.
        await expect(editor(page)).toContainText(`${flowId}_edited`)
    })
})
