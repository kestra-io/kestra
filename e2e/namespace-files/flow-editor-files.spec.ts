import {expect, test} from "./namespace-files.fixture"
import {FilesEditor, FLOW_TAB, fileTabId, flowSource} from "./namespace-files.helpers"

const FLOW_ID = "with_files"

// The flow editor saves every dirty file tab along with the flow. Those two save paths running into
// each other is what overwrote files with "undefined" (kestra-io/kestra-ee#4439, kestra-io/kestra#10981),
// so every test checks the stored bytes over the API rather than what the editor shows.
test.describe("Flow editor with namespace files open", () => {
    let editor: FilesEditor

    test.beforeEach(async ({page, namespace, flowsApi}) => {
        await flowsApi.createFlowViaApi(flowSource(namespace, FLOW_ID, "hello"))
        editor = new FilesEditor(page)
    })

    test("keeps a file's unsaved edit when the flow is saved with Ctrl+S", async ({namespace, filesApi, flowsApi}) => {
        const file = fileTabId("notes.txt")
        await filesApi.writeFile("notes.txt", "first line\n")

        await editor.gotoFlow(namespace, FLOW_ID)
        await editor.openFilesPanel()
        await editor.openFileFromTree("notes.txt")
        await editor.expectContent(file, "first line")

        await test.step("save a first edit from the file tab", async () => {
            await editor.typeAtEnd(file, "second line\n")
            await editor.pressSave()
            await expect.poll(() => filesApi.readFile("notes.txt")).toBe("first line\nsecond line\n")
            await expect(editor.dirtyMarker(file)).toHaveCount(0)
        })

        await test.step("leave a second edit unsaved", async () => {
            await editor.typeAtEnd(file, "third line")
            await expect(editor.dirtyMarker(file)).toBeVisible()
        })

        await test.step("edit the flow and save it with Ctrl+S", async () => {
            await editor.selectTab(FLOW_TAB)
            await editor.typeAtEnd(FLOW_TAB, "_edited")
            await editor.pressSave()
            await expect.poll(async () => (await flowsApi.getFlowViaApi(namespace, FLOW_ID)).source)
                .toBe(flowSource(namespace, FLOW_ID, "hello_edited"))
        })

        await expect.poll(() => filesApi.readFile("notes.txt")).toBe("first line\nsecond line\nthird line")
        await expect(editor.dirtyMarker(file)).toHaveCount(0)

        await test.step("reopen the editor and read both back", async () => {
            await editor.page.reload()
            await editor.selectTab(file)
            await editor.expectContent(file, "third line")
            await expect(editor.dirtyMarker(file)).toHaveCount(0)
            expect(await filesApi.readFile("notes.txt")).toBe("first line\nsecond line\nthird line")
        })
    })

    test("never writes an untouched open file while the flow is saved again and again", async ({namespace, filesApi, flowsApi}) => {
        const content = "print(\"héllo wörld ✓\")\r\n\tindented = {{ inputs.value }}\r\nlast line without newline"
        const file = fileTabId("script.py")
        await filesApi.writeFile("script.py", content)

        await editor.gotoFlow(namespace, FLOW_ID)
        await editor.openFilesPanel()
        await editor.openFileFromTree("script.py")
        await editor.expectContent(file, "last line without newline")
        await editor.selectTab(FLOW_TAB)

        for (const [round, save] of [[1, "button"], [2, "shortcut"], [3, "button"]] as const) {
            await test.step(`save the flow, round ${round} (${save})`, async () => {
                await editor.typeAtEnd(FLOW_TAB, `_${round}`)
                await (save === "button" ? editor.clickSave() : editor.pressSave())
                await expect.poll(async () => (await flowsApi.getFlowViaApi(namespace, FLOW_ID)).revision).toBe(round + 1)
            })
            // Activating the file tab reloads it from the server, which is where a stale read used to slip in.
            await editor.selectTab(file)
            await editor.selectTab(FLOW_TAB)
        }

        await test.step("save once more after a reload", async () => {
            await editor.page.reload()
            await editor.selectTab(FLOW_TAB)
            await editor.typeAtEnd(FLOW_TAB, "_4")
            await editor.pressSave()
            await expect.poll(async () => (await flowsApi.getFlowViaApi(namespace, FLOW_ID)).revision).toBe(5)
        })

        expect(await filesApi.readFile("script.py")).toBe(content)
        expect(await filesApi.revisionCount("script.py")).toBe(1)
    })

    test("saves each dirty file with its own content when only the files changed", async ({namespace, filesApi, flowsApi}) => {
        await filesApi.writeFile("a.txt", "alpha")
        await filesApi.writeFile("b.txt", "beta")

        await editor.gotoFlow(namespace, FLOW_ID)
        await editor.openFilesPanel()
        await editor.openFileFromTree("a.txt")
        await editor.expectContent(fileTabId("a.txt"), "alpha")
        await editor.typeAtEnd(fileTabId("a.txt"), " edited in a")
        await editor.openFileFromTree("b.txt")
        await editor.expectContent(fileTabId("b.txt"), "beta")
        await editor.typeAtEnd(fileTabId("b.txt"), " edited in b")

        await editor.selectTab(FLOW_TAB)
        await expect(editor.page.getByRole("button", {name: "Save", exact: true})).toBeEnabled()
        await editor.clickSave()

        await expect.poll(() => filesApi.readFile("a.txt")).toBe("alpha edited in a")
        await expect.poll(() => filesApi.readFile("b.txt")).toBe("beta edited in b")
        await expect(editor.dirtyMarker(fileTabId("a.txt"))).toHaveCount(0)
        await expect(editor.dirtyMarker(fileTabId("b.txt"))).toHaveCount(0)
        expect((await flowsApi.getFlowViaApi(namespace, FLOW_ID)).revision).toBe(1)
    })

    test("saves a file from its own tab without touching the flow", async ({namespace, filesApi, flowsApi}) => {
        const file = fileTabId("config.yml")
        await filesApi.writeFile("config.yml", "key: value\n")

        await editor.gotoFlow(namespace, FLOW_ID)
        await editor.openFilesPanel()
        await editor.openFileFromTree("config.yml")
        await editor.expectContent(file, "key: value")
        await editor.typeAtEnd(file, "other: value")
        await editor.pressSave()

        await expect.poll(() => filesApi.readFile("config.yml")).toBe("key: value\nother: value")
        await expect(editor.dirtyMarker(file)).toHaveCount(0)
        const flow = await flowsApi.getFlowViaApi(namespace, FLOW_ID)
        expect(flow.revision).toBe(1)
        expect(flow.source).toBe(flowSource(namespace, FLOW_ID, "hello"))
    })

    test("drops the edits of a file tab closed before the flow is saved", async ({namespace, filesApi, flowsApi}) => {
        const file = fileTabId("notes.txt")
        await filesApi.writeFile("notes.txt", "kept as is")

        await editor.gotoFlow(namespace, FLOW_ID)
        await editor.openFilesPanel()
        await editor.openFileFromTree("notes.txt")
        await editor.expectContent(file, "kept as is")
        await editor.typeAtEnd(file, " but edited")
        await expect(editor.dirtyMarker(file)).toBeVisible()
        await editor.closeTab(file)

        await editor.typeAtEnd(FLOW_TAB, "_edited")
        await editor.clickSave()
        await expect.poll(async () => (await flowsApi.getFlowViaApi(namespace, FLOW_ID)).revision).toBe(2)

        expect(await filesApi.readFile("notes.txt")).toBe("kept as is")
        expect(await filesApi.revisionCount("notes.txt")).toBe(1)
    })

    test("saves a file emptied in the editor as an empty file", async ({namespace, filesApi}) => {
        test.fail(true, "the flow's save-all skips a dirty tab whose content is \"\", so the file keeps its old content and stays dirty. Tracked in kestra-io/kestra-ee#4972")

        const file = fileTabId("notes.txt")
        await filesApi.writeFile("notes.txt", "to be removed")

        await editor.gotoFlow(namespace, FLOW_ID)
        await editor.openFilesPanel()
        await editor.openFileFromTree("notes.txt")
        await editor.expectContent(file, "to be removed")
        await editor.replaceContent(file, "")
        await expect(editor.dirtyMarker(file)).toBeVisible()

        await editor.selectTab(FLOW_TAB)
        await editor.clickSave()

        await expect.poll(() => filesApi.readFile("notes.txt"), {timeout: 5000}).toBe("")
        await expect(editor.dirtyMarker(file)).toHaveCount(0)
    })

    test("creates a file from the Files panel and saves what is typed in it", async ({namespace, filesApi}) => {
        const file = fileTabId("created.sh")

        await editor.gotoFlow(namespace, FLOW_ID)
        await editor.openFilesPanel()
        await editor.contentPanel("files").click({button: "right"})
        await editor.page.getByRole("menuitem", {name: "Create file"}).click()
        const dialog = editor.page.getByRole("dialog", {name: "Create file"})
        await dialog.getByRole("textbox").fill("created.sh")
        await dialog.getByRole("button", {name: "Create"}).click()

        await editor.waitForFileTab("created.sh")
        await expect.poll(() => filesApi.readFile("created.sh")).toBe("")

        await editor.typeAtEnd(file, "echo created")
        await editor.pressSave()

        await expect.poll(() => filesApi.readFile("created.sh")).toBe("echo created")
        await expect(editor.treeEntry("created.sh")).toBeVisible()
    })
})
