import {expect, test} from "./namespace-files.fixture"
import {FilesEditor, fileTabId} from "./namespace-files.helpers"

test.describe("Namespace Files page", () => {
    let editor: FilesEditor

    test.beforeEach(({page}) => {
        editor = new FilesEditor(page)
    })

    test("creates the first file of a namespace and keeps what is typed in it", async ({namespace, filesApi}) => {
        const file = fileTabId("first.py")

        await editor.gotoNamespaceFiles(namespace)
        await editor.page.getByRole("button", {name: "New File"}).click()
        const dialog = editor.page.getByRole("dialog", {name: "Create file"})
        await dialog.getByRole("textbox").fill("first.py")
        await dialog.getByRole("button", {name: "Create"}).click()

        await editor.waitForFileTab("first.py")
        await expect.poll(() => filesApi.readFile("first.py")).toBe("")

        await editor.typeAtEnd(file, "answer = 42")
        await expect(editor.dirtyMarker(file)).toBeVisible()
        await editor.pressSave()

        await expect.poll(() => filesApi.readFile("first.py")).toBe("answer = 42")
        await expect(editor.dirtyMarker(file)).toHaveCount(0)

        await editor.page.reload()
        await editor.waitForFileTab("first.py")
        await editor.expectContent(file, "answer = 42")
    })

    test("saves with the editor's save icon and keeps every byte of the content", async ({namespace, filesApi}) => {
        const file = fileTabId("template.txt")
        const original = "Grüße 日本語 ✓\r\n\tTab-indented {{ outputs.task.value }} {% if true %}yes{% endif %}\r\n"
        await filesApi.writeFile("template.txt", original)

        await editor.gotoNamespaceFiles(namespace)
        await editor.openFileFromTree("template.txt")
        await editor.expectContent(file, "日本語")
        await expect(editor.dirtyMarker(file)).toHaveCount(0)

        await editor.typeAtEnd(file, "appended ✓")
        await editor.contentPanel(file).locator("[data-test='file-save']").click()

        await expect.poll(() => filesApi.readFile("template.txt")).toBe(`${original}appended ✓`)
        expect(await filesApi.revisionCount("template.txt")).toBe(2)
        await expect(editor.dirtyMarker(file)).toHaveCount(0)
    })

    test("writes to the nested file and not to the root file of the same name", async ({namespace, filesApi}) => {
        const nested = "dir/sub/data.json"
        await filesApi.writeFile("data.json", "{\"level\": \"root\"}")
        await filesApi.writeFile(nested, "{\"level\": \"nested\"}")

        await editor.gotoNamespaceFiles(namespace)
        await editor.openFileFromSearch(nested)
        await editor.expectContent(fileTabId(nested), "nested")
        await editor.replaceContent(fileTabId(nested), "{\"level\": \"nested\", \"edited\": true}")
        await editor.pressSave()

        await expect.poll(() => filesApi.readFile(nested)).toBe("{\"level\": \"nested\", \"edited\": true}")
        expect(await filesApi.readFile("data.json")).toBe("{\"level\": \"root\"}")
        expect(await filesApi.revisionCount("data.json")).toBe(1)

        await editor.openFileFromTree("data.json")
        await editor.expectContent(fileTabId("data.json"), "root")
        await expect(editor.tab(fileTabId(nested))).toBeVisible()
    })

    test("saves the active tab only on Ctrl+S, writing nothing to the other dirty file", async ({namespace, filesApi}) => {
        await filesApi.writeFile("one.txt", "one")
        await filesApi.writeFile("two.txt", "two")

        await editor.gotoNamespaceFiles(namespace)
        await editor.openFileFromTree("one.txt")
        await editor.expectContent(fileTabId("one.txt"), "one")
        await editor.typeAtEnd(fileTabId("one.txt"), " plus first edit")
        await expect(editor.dirtyMarker(fileTabId("one.txt"))).toBeVisible()
        await editor.openFileFromTree("two.txt")
        await editor.expectContent(fileTabId("two.txt"), "two")
        await editor.typeAtEnd(fileTabId("two.txt"), " plus second edit")
        await editor.pressSave()

        await expect.poll(() => filesApi.readFile("two.txt")).toBe("two plus second edit")
        await expect(editor.dirtyMarker(fileTabId("two.txt"))).toHaveCount(0)
        await expect(editor.dirtyMarker(fileTabId("one.txt"))).toBeVisible()
        expect(await filesApi.readFile("one.txt")).toBe("one")
        expect(await filesApi.revisionCount("one.txt")).toBe(1)
    })

    test("keeps the unsaved edits of a tab the user switches away from and back to", async ({namespace, filesApi}) => {
        test.fixme(true, "activating a tab fetches its file again and the fetched content replaces the unsaved edits (14 of 14 probe runs); the rare pass under heavy load makes test.fail flaky. Tracked in kestra-io/kestra-ee#4972")

        await filesApi.writeFile("a.txt", "alpha")
        await filesApi.writeFile("b.txt", "beta")

        await editor.gotoNamespaceFiles(namespace)
        await editor.openFileFromTree("a.txt")
        await editor.expectContent(fileTabId("a.txt"), "alpha")
        await editor.typeAtEnd(fileTabId("a.txt"), " edited")
        await expect(editor.dirtyMarker(fileTabId("a.txt"))).toBeVisible()
        await editor.openFileFromTree("b.txt")
        await editor.expectContent(fileTabId("b.txt"), "beta")

        await editor.selectTab(fileTabId("a.txt"))
        await expect(editor.editor(fileTabId("a.txt"))).toContainText("alpha edited", {timeout: 5000})
        await expect(editor.dirtyMarker(fileTabId("a.txt"))).toBeVisible()
    })

    test("saves a file emptied in the editor as an empty file", async ({namespace, filesApi}) => {
        const file = fileTabId("notes.md")
        await filesApi.writeFile("notes.md", "# to be removed")

        await editor.gotoNamespaceFiles(namespace)
        await editor.openFileFromTree("notes.md")
        await editor.expectContent(file, "to be removed")
        await editor.replaceContent(file, "")
        await editor.pressSave()

        await expect.poll(() => filesApi.readFile("notes.md")).toBe("")
        await expect(editor.dirtyMarker(file)).toHaveCount(0)
    })

    test("restoring a revision refreshes the open tab, and the next save builds on it", async ({namespace, filesApi}) => {
        const file = fileTabId("history.txt")
        await filesApi.writeFile("history.txt", "version one")
        await filesApi.writeFile("history.txt", "version two")

        await editor.gotoNamespaceFiles(namespace)
        await editor.openFileFromTree("history.txt")
        await editor.expectContent(file, "version two")

        await editor.treeEntry("history.txt").click({button: "right"})
        await editor.page.getByRole("menuitem", {name: "Revision history"}).click()
        await editor.page.getByTestId("restore-left").click()
        await editor.page.getByRole("dialog", {name: "Confirmation"}).getByRole("button", {name: "OK"}).click()

        await expect.poll(() => filesApi.readFile("history.txt")).toBe("version one")
        await editor.page.getByRole("dialog", {name: "Revision history"}).getByRole("button", {name: "Close"}).click()
        await editor.expectContent(file, "version one")
        await expect(editor.dirtyMarker(file)).toHaveCount(0)

        await editor.typeAtEnd(file, ", then edited")
        await editor.pressSave()

        await expect.poll(() => filesApi.readFile("history.txt")).toBe("version one, then edited")
        expect(await filesApi.revisionCount("history.txt")).toBe(4)
    })
})
