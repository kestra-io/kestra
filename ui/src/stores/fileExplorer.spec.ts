import {beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {isDirectory, useFileExplorerStore, type TreeNodeDirectory} from "./fileExplorer"

const createDirectory = vi.fn()
const saveOrCreateFile = vi.fn()

vi.mock("override/stores/namespaces", () => ({
    useNamespacesStore: () => ({createDirectory, saveOrCreateFile}),
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
