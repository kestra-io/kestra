import {beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {isDirectory, useFileExplorerStore, type ElTreeNode, type TreeNodeDirectory} from "./fileExplorer"

const createDirectory = vi.fn()
const saveOrCreateFile = vi.fn()
const importFileDirectory = vi.fn()
const readDirectory = vi.fn()

vi.mock("override/stores/namespaces", () => ({
    useNamespacesStore: () => ({createDirectory, saveOrCreateFile, importFileDirectory, readDirectory}),
}))
vi.mock("vue-i18n", () => ({
    useI18n: () => ({t: (key: string) => key}),
}))
vi.mock("../utils/toast", () => ({
    useToast: () => ({success: vi.fn(), error: vi.fn()}),
}))

describe("fileExplorer store", () => {
    beforeEach(() => {
        vi.clearAllMocks()
        setActivePinia(createPinia())
    })

    function store() {
        const filesStore = useFileExplorerStore()
        filesStore.namespaceId = "io.kestra.test"
        return filesStore
    }

    it("should create the folder hierarchy when the file name is a path", async () => {
        const filesStore = store()

        const {path, file} = await filesStore.addFile({fileName: "/work/one", extension: "txt", leaf: true}, undefined, true)

        expect(saveOrCreateFile).toHaveBeenCalledWith({namespace: "io.kestra.test", path: "work/one.txt", content: ""})
        expect(path).toBe("work/one.txt")
        expect(file?.fileName).toBe("one.txt")

        const folder = filesStore.fileTree[0]
        expect(folder.fileName).toBe("work")
        expect(isDirectory(folder)).toBe(true)
        expect((folder as TreeNodeDirectory).children.map(child => child.fileName)).toEqual(["one.txt"])
    })

    it("should resolve the file name path against the selected parent folder", async () => {
        const filesStore = store()
        await filesStore.addFolder({fileName: "work"}, true)

        const {path} = await filesStore.addFile({fileName: "nested//one", extension: "txt", leaf: true}, "work", true)

        expect(saveOrCreateFile).toHaveBeenCalledWith({namespace: "io.kestra.test", path: "work/nested/one.txt", content: ""})
        expect(path).toBe("work/nested/one.txt")

        const work = filesStore.fileTree[0] as TreeNodeDirectory
        const nested = work.children[0] as TreeNodeDirectory
        expect(nested.fileName).toBe("nested")
        expect(nested.children.map(child => child.fileName)).toEqual(["one.txt"])
    })

    it("should create the folder hierarchy when the folder name is a path", async () => {
        const filesStore = store()

        await filesStore.addFolder({fileName: "/work/data/"}, true)

        expect(createDirectory).toHaveBeenCalledWith({namespace: "io.kestra.test", path: "work/data"})

        const work = filesStore.fileTree[0] as TreeNodeDirectory
        expect(work.fileName).toBe("work")
        expect(work.children.map(child => child.fileName)).toEqual(["data"])
    })

    it("should keep a listed file name verbatim, spaces included", async () => {
        const filesStore = store()

        const {path} = await filesStore.addFile({fileName: " spaced ", extension: "txt", leaf: true})

        expect(path).toBe(" spaced .txt")
        expect(filesStore.fileTree.map(node => node.fileName)).toEqual([" spaced .txt"])
    })

    it("should upload each imported file under its relative path and take the tree from the server", async () => {
        const filesStore = store()
        readDirectory.mockResolvedValue([{type: "File", fileName: "one.txt"}, {type: "File", fileName: "two.txt"}])
        const zip = new File(["PK"], "io.kestra.test_files.zip", {type: "application/zip"})
        const nested = new File(["print(1)"], "main.py")
        Object.defineProperty(nested, "webkitRelativePath", {value: "scripts/nested/main.py"})

        await filesStore.importFiles([zip, nested] as unknown as FileList)

        expect(importFileDirectory).toHaveBeenNthCalledWith(1, {namespace: "io.kestra.test", path: "io.kestra.test_files.zip", file: zip})
        expect(importFileDirectory).toHaveBeenNthCalledWith(2, {namespace: "io.kestra.test", path: "scripts/nested/main.py", file: nested})
        expect(filesStore.fileTree.map(node => node.fileName)).toEqual(["one.txt", "two.txt"])
    })

    it("should still reload the tree when one of the uploads fails", async () => {
        const filesStore = store()
        readDirectory.mockResolvedValue([{type: "File", fileName: "one.txt"}])
        importFileDirectory.mockResolvedValueOnce(undefined).mockRejectedValueOnce(new Error("403"))

        await expect(filesStore.importFiles([new File([""], "one.txt"), new File([""], "two.txt")] as unknown as FileList)).rejects.toThrow("403")

        expect(filesStore.fileTree.map(node => node.fileName)).toEqual(["one.txt"])
    })

    it("should finish the root load and not reject when the directory read fails", async () => {
        const filesStore = store()
        readDirectory.mockRejectedValueOnce(Object.assign(new Error("Directory not found"), {status: 404}))

        // Must not reject: loadNodes is called fire-and-forget, so a throw becomes an unhandled rejection.
        await expect(filesStore.loadNodes()).resolves.toBeUndefined()

        // rootLoaded must flip true, otherwise NamespaceFilesEditorView spins forever over the whole browser.
        expect(filesStore.rootLoaded).toBe(true)
    })

    it("should clear the tree when the root load fails so a stale namespace's files are not shown", async () => {
        const filesStore = store()
        readDirectory.mockResolvedValueOnce([{type: "File", fileName: "one.txt"}])
        await filesStore.loadNodes()
        expect(filesStore.fileTree).toHaveLength(1)

        // The store is a singleton across navigation: a failed load must not leave the previous
        // namespace's files rendered as the new one's.
        readDirectory.mockRejectedValueOnce(Object.assign(new Error("boom"), {status: 500}))
        await filesStore.loadNodes()

        expect(filesStore.fileTree).toEqual([])
    })

    it("should resolve a failed sub-folder expansion as empty rather than leaving it stuck loading", async () => {
        const filesStore = store()
        readDirectory
            .mockResolvedValueOnce([{type: "Directory", fileName: "gone"}])
            .mockRejectedValueOnce(Object.assign(new Error("Directory not found"), {status: 404}))
        await filesStore.loadNodes()
        const folder = filesStore.fileTree[0]

        const resolve = vi.fn()
        await filesStore.loadNodes({level: 1, data: {id: folder.id}} as unknown as ElTreeNode, resolve)

        expect(resolve).toHaveBeenCalledWith([])
    })

    it("should propagate a non-404 sub-folder expansion failure instead of hiding the folder", async () => {
        const filesStore = store()
        readDirectory
            .mockResolvedValueOnce([{type: "Directory", fileName: "dir"}])
            .mockRejectedValueOnce(Object.assign(new Error("boom"), {status: 500}))
        await filesStore.loadNodes()
        const folder = filesStore.fileTree[0]

        const resolve = vi.fn()
        const reject = vi.fn()
        // A transient error must not resolve the node as empty (el-tree would cache it and hide the real children).
        await expect(filesStore.loadNodes({level: 1, data: {id: folder.id}} as unknown as ElTreeNode, resolve, reject)).rejects.toThrow("boom")
        expect(resolve).not.toHaveBeenCalled()
        // el-tree clears its `loading` flag only through `reject`; without it the node is pinned spinning.
        expect(reject).toHaveBeenCalled()
    })

    it("should not create anything when the name only holds separators", async () => {
        const filesStore = store()

        await filesStore.addFolder({fileName: "//"}, true)
        const {path} = await filesStore.addFile({fileName: "/", extension: "", leaf: true}, undefined, true)

        expect(createDirectory).not.toHaveBeenCalled()
        expect(saveOrCreateFile).not.toHaveBeenCalled()
        expect(path).toBeUndefined()
        expect(filesStore.fileTree).toEqual([])
    })
})
