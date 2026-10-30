import {expect, test} from "./blocks.fixture"
import {FlowsApi} from "../api/flows.api"
import {fetchFlowSource, login, openBlockEditor, walkTo} from "./blocks.helpers"

// DAG tasks are stored as {task, dependsOn} wrappers, unlike every other
// flowable's flat task list — covers rendering, editing and dependsOn
// persistence through that wrapper shape without breaking flat lanes.
test.describe("Block editor — Dag task wrapper", () => {
    let flowsApi: FlowsApi
    let flowId: string

    test.beforeEach(async ({page, request, baseURL}) => {
        flowsApi = new FlowsApi(request, baseURL)
        flowId = await flowsApi.generateFlowViaApi("blocks-dag.yaml", "blocks-dag-fixture")
        await login(page)
        await openBlockEditor(page, flowId)
    })

    test.afterEach(async () => {
        await flowsApi.removeFlowsViaApi()
    })

    test("sub-task cards show their real ids instead of rendering blank", async ({page}) => {
        await walkTo(page, "my_dag")
        await page.keyboard.press("ArrowRight")

        const cardA = page.locator("[data-block-id='a']")
        const cardB = page.locator("[data-block-id='b']")
        const cardC = page.locator("[data-block-id='c']")
        await expect(cardA).toBeVisible()
        await expect(cardB).toBeVisible()
        await expect(cardC).toBeVisible()
        await expect(cardA.locator("[data-test='block-card-id']")).toHaveText("a")
        await expect(cardA.locator("[data-test='block-card-type']")).toHaveText("Log")
        await expect(cardB.locator("[data-test='block-card-id']")).toHaveText("b")
    })

    test("opening a DAG sub-task edits the inner task, not the wrapper", async ({page}) => {
        await walkTo(page, "my_dag")
        await page.keyboard.press("ArrowRight")
        await walkTo(page, "a")
        await page.keyboard.press("Enter")

        const dock = page.locator("[data-test='block-editor-task-edit']")
        await expect(dock).toBeVisible()
        await expect(dock).toContainText("message")
        // The tab label is "my_dag / a" — the wrapped task's id, not the
        // wrapper's own array index (which has no id of its own).
        await expect(page.getByRole("tab", {name: "my_dag / a Close"})).toBeVisible()
    })

    test("dependsOn shows only sibling ids and persists through the wrapper on save", async ({page, request, baseURL}) => {
        await walkTo(page, "my_dag")
        await page.keyboard.press("ArrowRight")

        // "c" starts with no dependsOn — setting it to depend on "a" cannot cycle
        // (unlike editing "a" or "b", which already form an a -> b edge).
        const cardC = page.locator("[data-block-id='c']")
        const dependsOnC = cardC.locator("xpath=following-sibling::*[1]").filter({hasText: "Depends on"})
        const select = dependsOnC.locator("[data-test='dag-depends-on-select']")
        await select.click()

        const listbox = page.getByRole("listbox", {name: "Depends on"})
        await expect(listbox.getByRole("option", {name: "a", exact: true})).toBeVisible()
        await expect(listbox.getByRole("option", {name: "b", exact: true})).toBeVisible()
        // "c" cannot depend on itself, so it must not be offered as an option
        await expect(listbox.getByRole("option", {name: "c", exact: true})).toHaveCount(0)

        await listbox.getByRole("option", {name: "a", exact: true}).click()
        await page.keyboard.press("Escape")

        await page.keyboard.press("ControlOrMeta+s")
        await expect(page.getByText("Successfully saved", {exact: false}).first()).toBeVisible()

        const source = await fetchFlowSource(request, baseURL!, flowId)
        expect(source).toContain("task:")
        expect(source).toMatch(/id:\s*c[\s\S]*dependsOn:\s*\n\s*-\s*a/)
        // pre-existing wrapper shape and sibling dependsOn are untouched
        expect(source).toMatch(/id:\s*b[\s\S]*dependsOn:\s*\n\s*-\s*a/)
    })
})
