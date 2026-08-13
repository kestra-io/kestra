import {expect, test} from "./fixtures/auth"
import type {Page} from "@playwright/test"
import {v4 as uuidv4} from "uuid"
import fs from "fs"
import {fileURLToPath} from "url"
import path from "path"

// The repo doesn't set `testIdAttribute`, so `data-test` is matched by hand.
const CREATE = "[data-test=\"flows-create\"]"
const LANDING = "[data-test=\"new-flow-landing\"]"

/**
 * `/flows/new` opens the creation funnel, so the editor is one step behind the blank-flow
 * form. Walked as a user would rather than bypassed with a query flag, so the specs keep
 * covering the real entry point into the editor.
 */
const openEditorFromFunnel = async (page: Page, id: string, namespace: string) => {
    await page.locator(CREATE).click()
    await page.waitForURL("**/flows/new")
    await expect(page.locator(LANDING)).toBeVisible()

    await page.locator("[data-test=\"blank-flow-id\"]").fill(id)

    // The namespace select is filterable with `allowCreate`, so typing a namespace that
    // does not exist yet and confirming it is what creates the option.
    await page.locator("[data-test=\"blank-flow-namespace\"]").click()
    await page.keyboard.type(namespace)
    await page.keyboard.press("Enter")

    await expect(page.locator("[data-test=\"blank-flow-open-editor\"]")).toBeEnabled()
    await page.locator("[data-test=\"blank-flow-open-editor\"]").click()
    await expect(page.locator(LANDING)).toBeHidden()
}

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

const helloFlowYaml = fs.readFileSync(
    path.resolve(__dirname, "./fixtures/flows/hello.yaml"),
    "utf-8",
)
const helloFlowId = "my-hello-flow-1"

test.describe("Flow Page", () => {

    let testUUID = ""

    // Both tests create and run real flows against the single shared Kestra instance, so
    // keep them in one worker. `default` rather than `serial`: `serial` would skip the
    // remaining tests after a failure and hide a second regression.
    test.describe.configure({mode: "default"})

    test.beforeEach(() => {
        testUUID = uuidv4().replace(/-/g, "_")
    })

    test("should create and execute the example Flow", async ({page}) => {
        await page.goto("/ui/flows")

        await test.step("create the example Flow", async () => {
            await page.waitForURL("**/flows")

            await openEditorFromFunnel(page, `flow_${testUUID}`.slice(0, 19), "company.team")

            await page.getByRole("button", {name: "Save", exact: true}).click()
            await expect(page.getByRole("heading", {name: "Successfully saved"})).toBeVisible()
            await page.locator(".tab-select").click()
            await page.getByRole("option", {name: "Overview"}).click()
        })

        await test.step("execute the flow", async () => {

            await page.getByRole("button", {name: "Execute"}).first().click()

            await page.getByRole("dialog").getByRole("button", {name: "Execute"}).click()

            await page.getByText("hello", {exact: true}).click()// default task log
            await expect(page.getByText("Hello World!")).toBeVisible({timeout: 10000})
        })
    })

    test("should create and execute a Flow with input", async ({page, context}) => {
        await context.grantPermissions(["clipboard-read", "clipboard-write"])

        const flowId = `flowId_${testUUID}`.slice(0, 19)
        const flowYaml = helloFlowYaml.replace(helloFlowId, flowId)

        await page.goto("/ui/flows")

        await test.step("create a the flow by pasting the YAML", async () => {
            await expect(page.locator(CREATE)).toBeVisible()
            await openEditorFromFunnel(page, flowId, "company.team")
            // Must be awaited as an assertion: the `clear({force: true})` below skips
            // actionability checks, so it would happily fire into an unmounted editor.
            await expect(page.getByTestId("monaco-editor").getByText("Hello World")).toBeVisible()

            const monacoEditor = page.getByTestId("monaco-editor-hidden-synced-textarea")
            await monacoEditor.clear({force: true})
            await expect(page.getByTestId("monaco-editor").getByText("Hello World")).not.toBeVisible()
            await monacoEditor.fill(flowYaml, {force: true})
            await monacoEditor.blur()
            await expect(page.getByTestId("monaco-editor").getByText(flowId)).toBeVisible()

            await page.getByRole("button", {name: "Save", exact: true}).click()
            await expect(page.getByRole("heading", {name: "Successfully saved"})).toBeVisible()
            await page.locator(".tab-select").click()
            await page.getByRole("option", {name: "Overview"}).click()
            // Scoped to the topnav title, not #app: the Monaco editor (still disposing during
            // the tab switch) also contains the flowId, so a broader match is a strict-mode
            // violation, not a sign the navigation failed.
            await expect(page.locator("#topnav-title-slot").getByText(flowId)).toBeVisible()
        })

        const inputValue = "my-input_" + testUUID
        await test.step("execute the flow with INPUT_A: " + inputValue, async () => {

            await page.getByRole("button", {name: "Execute"}).first().click()

            await expect(page.getByRole("dialog").getByText("INPUT_A", {exact: true})).toBeVisible()
            await page.getByRole("dialog").getByTestId("monaco-editor").getByRole("textbox").fill(inputValue)
            await expect(page.getByRole("dialog").getByRole("button", {name: "Execute"})).toBeEnabled()
            await page.getByRole("dialog").getByRole("button", {name: "Execute"}).click()

            await page.getByText("log_hello_task").click()
            await expect(page.getByText(inputValue)
                .first(),// TODO this is probably a hack, but at least it's fixing the test
            ).toBeVisible()
        })
    })
})

