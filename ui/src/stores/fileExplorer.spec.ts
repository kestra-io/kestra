import {beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {isDirectory, useFileExplorerStore, getFileNameWithExtension, type ElTreeNode, type TreeNodeDirectory} from "./fileExplorer"

const createDirectory = vi.fn()
const saveOrCreateFile = vi.fn()
const importFileDirectory = vi.fn()
const readDirectory = vi.fn()
const toastSuccess = vi.fn()
const toastError = vi.fn()

vi.mock("override/stores/namespaces", () => ({
    useNamespacesStore: () => ({createDirectory, saveOrCreateFile, importFileDirectory, readDirectory}),
}))
vi.mock("../utils/toast", () => ({
    makeToast: () => ({success: toastSuccess, error: toastError}),
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
        const error = vi.spyOn(console, "error").mockImplementation(() => {})
        readDirectory.mockRejectedValueOnce(Object.assign(new Error("Directory not found"), {status: 404}))

        // Must not reject: loadNodes is called fire-and-forget, so a throw becomes an unhandled rejection.
        await expect(filesStore.loadNodes()).resolves.toBeUndefined()

        // rootLoaded must flip true, otherwise NamespaceFilesEditorView spins forever over the whole browser.
        expect(filesStore.rootLoaded).toBe(true)
        expect(error).toHaveBeenCalledOnce()
        error.mockRestore()
    })

    it("should leave an entry without a name out of the tree instead of rendering it", async () => {
        const filesStore = store()
        readDirectory.mockResolvedValueOnce([{type: "File"}, {type: "File", fileName: "one.txt"}])

        await filesStore.loadNodes()

        expect(filesStore.fileTree.map(node => node.fileName)).toEqual(["one.txt"])
    })

    it("should clear the tree when the root load fails so a stale namespace's files are not shown", async () => {
        const filesStore = store()
        readDirectory.mockResolvedValueOnce([{type: "File", fileName: "one.txt"}])
        await filesStore.loadNodes()
        expect(filesStore.fileTree).toHaveLength(1)

        // The store is a singleton across navigation: a failed load must not leave the previous
        // namespace's files rendered as the new one's.
        readDirectory.mockRejectedValueOnce(Object.assign(new Error("boom"), {status: 500}))
        const error = vi.spyOn(console, "error").mockImplementation(() => {})
        await filesStore.loadNodes()

        expect(filesStore.fileTree).toEqual([])
        error.mockRestore()
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

    it("should keep a file name that already carries its extension unchanged", async () => {
        const filesStore = store()

        const {path, file} = await filesStore.addFile({fileName: "one.txt", extension: "", leaf: true}, undefined, true)

        expect(saveOrCreateFile).toHaveBeenCalledWith({namespace: "io.kestra.test", path: "one.txt", content: ""})
        expect(path).toBe("one.txt")
        expect(file?.fileName).toBe("one.txt")
        expect(file?.extension).toBe("txt")
    })

    it("should keep a dot that is not the extension and append the configured extension", async () => {
        const filesStore = store()

        const {path, file} = await filesStore.addFile({fileName: "my.file", extension: "py", leaf: true}, undefined, true)

        expect(saveOrCreateFile).toHaveBeenCalledWith({namespace: "io.kestra.test", path: "my.file.py", content: ""})
        expect(path).toBe("my.file.py")
        expect(file?.fileName).toBe("my.file.py")
        expect(file?.extension).toBe("py")
    })

    it("should block creating a file in a collapsed folder when that file already exists on the server", async () => {
        const filesStore = store()
        readDirectory
            .mockResolvedValueOnce([{type: "Directory", fileName: "scripts"}])
            .mockResolvedValueOnce([{type: "File", fileName: "main.py"}])
        await filesStore.loadNodes()

        const result = await filesStore.addFile({fileName: "main", extension: "py", leaf: true}, "scripts", true)

        expect(result).toEqual({})
        expect(saveOrCreateFile).not.toHaveBeenCalled()
        expect(toastError).toHaveBeenCalledWith("namespace files.create.file_already_exists")
    })

    it("should block creating a file via a path into a collapsed folder when that file already exists", async () => {
        const filesStore = store()
        readDirectory
            .mockResolvedValueOnce([{type: "Directory", fileName: "scripts"}])
            .mockResolvedValueOnce([{type: "File", fileName: "main.py"}])
        await filesStore.loadNodes()

        const result = await filesStore.addFile({fileName: "scripts/main", extension: "py", leaf: true}, undefined, true)

        expect(result).toEqual({})
        expect(saveOrCreateFile).not.toHaveBeenCalled()
        expect(toastError).toHaveBeenCalledWith("namespace files.create.file_already_exists")
    })

    it("should block creating a folder in a collapsed folder when that folder already exists on the server", async () => {
        const filesStore = store()
        readDirectory
            .mockResolvedValueOnce([{type: "Directory", fileName: "scripts"}])
            .mockResolvedValueOnce([{type: "Directory", fileName: "utils"}])
        await filesStore.loadNodes()

        await filesStore.addFolder({parentPath: "scripts", fileName: "utils"}, true)

        expect(createDirectory).not.toHaveBeenCalled()
        expect(toastError).toHaveBeenCalledWith("namespace files.create.folder_already_exists")
    })

    it("should create a nested path inside a lazy-loaded subfolder without throwing", async () => {
        const filesStore = store()
        readDirectory
            .mockResolvedValueOnce([{type: "Directory", fileName: "parent"}])
            .mockResolvedValueOnce([{type: "Directory", fileName: "sub"}])
            .mockResolvedValueOnce([{type: "File", fileName: "existing.txt"}])
        await filesStore.loadNodes()
        const parent = filesStore.fileTree[0] as TreeNodeDirectory

        await filesStore.loadNodes({level: 1, data: {id: parent.id}} as unknown as ElTreeNode, vi.fn())
        const sub = parent.children[0] as TreeNodeDirectory
        expect(sub.children).toEqual([])

        const {path, file} = await filesStore.addFile({fileName: "parent/sub/a/new", extension: "py", leaf: true}, undefined, true)

        expect(saveOrCreateFile).toHaveBeenCalledWith({namespace: "io.kestra.test", path: "parent/sub/a/new.py", content: ""})
        expect(path).toBe("parent/sub/a/new.py")
        expect(file?.fileName).toBe("new.py")
        const aFolder = sub.children.find(node => node.fileName === "a") as TreeNodeDirectory
        expect(aFolder).toBeDefined()
        expect(aFolder.children.map(node => node.fileName)).toEqual(["new.py"])
    })

    it("should update children when expanding a nested directory even when sibling files are present", async () => {
        const filesStore = store()
        readDirectory
            .mockResolvedValueOnce([{type: "Directory", fileName: "parent"}])
            .mockResolvedValueOnce([{type: "File", fileName: "readme.md"}, {type: "Directory", fileName: "sub"}])
            .mockResolvedValueOnce([{type: "File", fileName: "nested.py"}])
        await filesStore.loadNodes()
        const parent = filesStore.fileTree[0] as TreeNodeDirectory
        await filesStore.loadNodes({level: 1, data: {id: parent.id}} as unknown as ElTreeNode, vi.fn())
        const sub = parent.children.find(node => node.fileName === "sub") as TreeNodeDirectory

        await filesStore.loadNodes({level: 2, data: {id: sub.id}} as unknown as ElTreeNode, vi.fn())

        expect(sub.children.map(node => node.fileName)).toEqual(["nested.py"])
    })

    it("should find the expanded directory even when a file precedes it in the tree", async () => {
        const filesStore = store()
        readDirectory
            .mockResolvedValueOnce([{type: "Directory", fileName: "parent"}])
            .mockResolvedValueOnce([{type: "File", fileName: "nested.py"}])
        await filesStore.loadNodes()
        const parent = filesStore.fileTree[0]
        filesStore.fileTree = [{id: "readme", fileName: "readme.md", type: "File", leaf: true}, parent]

        await filesStore.loadNodes({level: 1, data: {id: parent.id}} as unknown as ElTreeNode, vi.fn())

        const expanded = filesStore.fileTree[1] as TreeNodeDirectory
        expect(expanded.children.map(node => node.fileName)).toEqual(["nested.py"])
    })

    it("should add a nested path under a directory that has no children array", async () => {
        const filesStore = store()
        readDirectory.mockResolvedValueOnce([])
        await filesStore.loadNodes()
        filesStore.fileTree = [{id: "parent", fileName: "parent", type: "Directory", leaf: false} as unknown as TreeNodeDirectory]

        const {path} = await filesStore.addFile({fileName: "parent/a/new", extension: "py", leaf: true})

        expect(path).toBe("parent/a/new.py")
        const parent = filesStore.fileTree[0] as TreeNodeDirectory
        const aFolder = parent.children.find(node => node.fileName === "a") as TreeNodeDirectory
        expect(aFolder.children.map(node => node.fileName)).toEqual(["new.py"])
    })

    it("should treat a 404 when reading a collapsed folder as empty and still create the file", async () => {
        const filesStore = store()
        readDirectory
            .mockResolvedValueOnce([{type: "Directory", fileName: "scripts"}])
            .mockRejectedValueOnce(Object.assign(new Error("Directory not found"), {status: 404}))
        await filesStore.loadNodes()

        const {path} = await filesStore.addFile({fileName: "main", extension: "py", leaf: true}, "scripts", true)

        expect(saveOrCreateFile).toHaveBeenCalledWith({namespace: "io.kestra.test", path: "scripts/main.py", content: ""})
        expect(path).toBe("scripts/main.py")
        expect(toastError).not.toHaveBeenCalled()
    })

    it("should not create the file when reading the collapsed folder fails", async () => {
        const filesStore = store()
        readDirectory
            .mockResolvedValueOnce([{type: "Directory", fileName: "scripts"}])
            .mockRejectedValueOnce(Object.assign(new Error("boom"), {status: 500}))
        await filesStore.loadNodes()

        const result = await filesStore.addFile({fileName: "main", extension: "py", leaf: true}, "scripts", true)

        expect(result).toEqual({})
        expect(saveOrCreateFile).not.toHaveBeenCalled()
        expect(toastError).toHaveBeenCalledWith("namespace files.create.file_error")
    })

    it("should not create the folder when reading the collapsed parent fails", async () => {
        const filesStore = store()
        readDirectory
            .mockResolvedValueOnce([{type: "Directory", fileName: "scripts"}])
            .mockRejectedValueOnce(Object.assign(new Error("boom"), {status: 500}))
        await filesStore.loadNodes()

        await filesStore.addFolder({parentPath: "scripts", fileName: "utils"}, true)

        expect(createDirectory).not.toHaveBeenCalled()
        expect(toastError).toHaveBeenCalledWith("namespace files.create.folder_error")
    })

    it("should block creation when a file already holds one of the folder names in the path", async () => {
        const filesStore = store()
        readDirectory.mockResolvedValueOnce([{type: "File", fileName: "main.py"}])
        await filesStore.loadNodes()

        const result = await filesStore.addFile({fileName: "main.py/other", extension: "txt", leaf: true}, undefined, true)

        expect(result).toEqual({})
        expect(saveOrCreateFile).not.toHaveBeenCalled()
        expect(toastError).toHaveBeenCalledWith("namespace files.create.folder_conflicts_with_file")
    })

    it("should read a collapsed folder only once across several creations", async () => {
        const filesStore = store()
        readDirectory
            .mockResolvedValueOnce([{type: "Directory", fileName: "scripts"}])
            .mockResolvedValueOnce([])
        await filesStore.loadNodes()

        await filesStore.addFile({fileName: "one", extension: "py", leaf: true}, "scripts", true)
        await filesStore.addFile({fileName: "two", extension: "py", leaf: true}, "scripts", true)
        await filesStore.addFolder({parentPath: "scripts", fileName: "utils"}, true)

        expect(readDirectory).toHaveBeenCalledTimes(2)
        expect(saveOrCreateFile).toHaveBeenCalledTimes(2)
        expect(createDirectory).toHaveBeenCalledTimes(1)
    })

    it("should not read folders that were created from the explorer", async () => {
        const filesStore = store()
        readDirectory.mockResolvedValueOnce([])
        await filesStore.loadNodes()

        await filesStore.addFile({fileName: "x/y/one", extension: "txt", leaf: true}, undefined, true)
        await filesStore.addFile({fileName: "x/y/two", extension: "txt", leaf: true}, undefined, true)
        await filesStore.addFolder({fileName: "z"}, true)
        await filesStore.addFile({fileName: "three", extension: "txt", leaf: true}, "z", true)

        expect(readDirectory).toHaveBeenCalledTimes(1)
        expect(saveOrCreateFile).toHaveBeenCalledTimes(3)
    })
})

describe("getFileNameWithExtension", () => {
    it.each([
        ["one.txt", ["one", "txt"]],
        ["noextension", ["noextension", ""]],
        ["archive.tar.gz", ["archive.tar", "gz"]],
        [".txt", ["", "txt"]],
        ["", ["", ""]],
    ])("should split %j at the last dot into %j", (input, expected) => {
        expect(getFileNameWithExtension(input)).toEqual(expected)
    })

    it("should preserve the case of the name and the extension", () => {
        expect(getFileNameWithExtension("README.MD")).toEqual(["README", "MD"])
    })
})
