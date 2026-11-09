import {beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {defineComponent, h} from "vue"
import {flushPromises} from "@vue/test-utils"
import {i18nShallowMount} from "../../../tests/unit/i18nMount"

type Node = {data: {id: string; fileName?: string; leaf?: boolean}; parent?: Node}

const {moveFileDirectory, renameFileDirectory, deleteFileDirectory, store} = vi.hoisted(() => ({
    moveFileDirectory: vi.fn(),
    renameFileDirectory: vi.fn(),
    deleteFileDirectory: vi.fn(),
    store: {fileTree: [] as unknown[], paths: {} as Record<string, string>},
}))

vi.mock("vue-router", async (importOriginal) => ({
    ...(await importOriginal<typeof import("vue-router")>()),
    useRoute: () => ({params: {namespace: "company"}, query: {}}),
}))

vi.mock("override/stores/namespaces", () => ({
    useNamespacesStore: () => ({moveFileDirectory, renameFileDirectory, deleteFileDirectory}),
}))

vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({user: {isAllowed: () => true}}),
}))

vi.mock("../../utils/toast", () => ({
    useToast: () => ({success: vi.fn(), error: vi.fn(), warning: vi.fn(), confirm: vi.fn(), saved: vi.fn(), deleted: vi.fn()}),
}))

vi.mock("../../stores/fileExplorer", async (importOriginal) => ({
    ...(await importOriginal<typeof import("../../stores/fileExplorer")>()),
    useFileExplorerStore: () => ({
        namespaceId: "company",
        get fileTree() {
            return store.fileTree
        },
        getPath: (id: string) => store.paths[id],
        loadNodes: vi.fn(),
    }),
}))

import FileExplorer, {FILES_CLOSE_TAB_INJECTION_KEY, FILES_MOVE_TABS_INJECTION_KEY, FILES_OPEN_TAB_INJECTION_KEY} from "./FileExplorer.vue"

const root: Node = {data: {id: "root"}}
const docs: Node = {data: {id: "docs"}, parent: root}
const readme: Node = {data: {id: "readme", fileName: "README.md", leaf: true}, parent: root}
const changelog: Node = {data: {id: "changelog", fileName: "CHANGELOG.md", leaf: true}, parent: root}
const contributing: Node = {data: {id: "contributing", fileName: "CONTRIBUTING.md", leaf: true}, parent: docs}

let rows: Node[] = []
const closeTab = vi.fn(() => true)
const moveTabs = vi.fn()

const KsTree = defineComponent({
    name: "KsTree",
    props: {allowDrop: {type: Function, default: undefined}},
    emits: ["node-drag-start", "node-drop"],
    methods: {
        setCurrentKey() {},
        remove() {},
        append() {},
        // The tree looks nodes up by id. A miss must stay empty so a rename does not throw.
        getNode(key: string | Node) {
            if (typeof key === "object" && key?.data) {
                return key
            }
            return [...rows, readme, docs, changelog, contributing].find((row) => row.data.id === key)
        },
    },
    render() {
        return h("div", rows.map((node) => h("div", {key: node.data.id}, this.$slots.default?.({node, data: node.data}))))
    },
})

function mountExplorer() {
    return i18nShallowMount(FileExplorer, {
        global: {
            plugins: [createPinia()],
            provide: {
                [FILES_OPEN_TAB_INJECTION_KEY as symbol]: vi.fn(),
                [FILES_CLOSE_TAB_INJECTION_KEY as symbol]: closeTab,
                [FILES_MOVE_TABS_INJECTION_KEY as symbol]: moveTabs,
            },
            stubs: {KsTree, KsDropdown: {template: "<div><slot /></div>"}},
        },
    })
}

function tree(wrapper: ReturnType<typeof mountExplorer>) {
    return wrapper.findComponent({name: "KsTree"})
}

async function select(wrapper: ReturnType<typeof mountExplorer>, ...nodes: Node[]) {
    const hitboxes = wrapper.findAll(".tree-node-hitbox")
    for (const node of nodes) {
        await hitboxes[rows.indexOf(node)].trigger("click", {metaKey: true})
    }
}

async function dragAndDrop(wrapper: ReturnType<typeof mountExplorer>, node: Node, pathAfter?: string) {
    tree(wrapper).vm.$emit("node-drag-start", node)
    if (pathAfter !== undefined) {
        store.paths[node.data.id] = pathAfter
    }
    tree(wrapper).vm.$emit("node-drop", node)
    await flushPromises()
}

describe("FileExplorer drag and drop", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        moveFileDirectory.mockReset().mockResolvedValue(undefined)
        moveTabs.mockReset()
        store.fileTree = [
            {id: "readme", fileName: "README.md", type: "File"},
            {id: "changelog", fileName: "CHANGELOG.md", type: "File"},
            {id: "docs", fileName: "docs", type: "Directory", children: [{id: "contributing", fileName: "CONTRIBUTING.md", type: "File"}]},
        ]
        store.paths = {readme: "README.md", changelog: "CHANGELOG.md", docs: "docs", contributing: "docs/CONTRIBUTING.md"}
        rows = [readme, changelog, contributing]
    })

    it("does not ask the server to move a file dropped back on its own path", async () => {
        await dragAndDrop(mountExplorer(), readme)

        expect(moveFileDirectory).not.toHaveBeenCalled()
    })

    it("does not offer a drop that leaves a file in its own folder", () => {
        const allowDrop = tree(mountExplorer()).props("allowDrop")

        expect(allowDrop(readme, changelog, "next")).toBe(false)
        expect(allowDrop(readme, docs, "inner")).toBe(true)
        expect(allowDrop(readme, changelog, "inner")).toBe(false)
    })

    it("moves a file dropped into another folder", async () => {
        await dragAndDrop(mountExplorer(), readme, "docs/README.md")

        expect(moveFileDirectory).toHaveBeenCalledWith({namespace: "company", old: "README.md", new: "docs/README.md"})
        expect(moveTabs).toHaveBeenCalledWith({from: "README.md", to: "docs/README.md"})
    })

    it("leaves the open tabs alone when the server refuses the move", async () => {
        moveFileDirectory.mockRejectedValue(new Error("refused"))

        await dragAndDrop(mountExplorer(), readme, "docs/README.md")

        expect(moveTabs).not.toHaveBeenCalled()
    })

    it("sends nothing for a bulk drop when every selected file is already in the target folder", async () => {
        const wrapper = mountExplorer()
        await select(wrapper, readme, changelog)

        tree(wrapper).vm.$emit("node-drag-start", readme)
        expect(tree(wrapper).props("allowDrop")(readme, changelog, "next")).toBe(true)
        tree(wrapper).vm.$emit("node-drop", readme)
        await flushPromises()

        expect(moveFileDirectory).not.toHaveBeenCalled()
    })

    it("moves only the selected files whose path changes when the dragged file stays put", async () => {
        const wrapper = mountExplorer()
        await select(wrapper, readme, contributing)

        await dragAndDrop(wrapper, readme)

        expect(moveFileDirectory).toHaveBeenCalledTimes(1)
        expect(moveFileDirectory).toHaveBeenCalledWith({namespace: "company", old: "docs/CONTRIBUTING.md", new: "CONTRIBUTING.md"})
        expect(moveTabs).toHaveBeenCalledWith({from: "docs/CONTRIBUTING.md", to: "CONTRIBUTING.md"})
    })
})

describe("FileExplorer rename and delete", () => {
    type ExplorerVm = {
        toggleRenameDialog: (shown: boolean, type: "file" | "folder", name: string, node: Node) => void
        renameItem: () => Promise<void>
        confirmRemove: (nodes: unknown[]) => void
        removeItems: () => Promise<void>
    }

    beforeEach(() => {
        setActivePinia(createPinia())
        renameFileDirectory.mockReset().mockResolvedValue(undefined)
        deleteFileDirectory.mockReset().mockResolvedValue(undefined)
        closeTab.mockClear()
        moveTabs.mockReset()
        store.fileTree = []
        store.paths = {readme: "README.md", docs: "docs", contributing: "docs/CONTRIBUTING.md"}
        rows = []
    })

    it("moves the open tab of a renamed file, so its unsaved edits follow it", async () => {
        const vm = mountExplorer().vm as unknown as ExplorerVm

        vm.toggleRenameDialog(true, "file", "README.md", readme)
        Object.assign((vm as unknown as {renameDialog: {name: string}}).renameDialog, {name: "NOTES.md"})
        await vm.renameItem()

        expect(renameFileDirectory).toHaveBeenCalledWith({namespace: "company", old: "README.md", new: "NOTES.md"})
        expect(moveTabs).toHaveBeenCalledWith({from: "README.md", to: "NOTES.md"})
        expect(closeTab).not.toHaveBeenCalled()
    })

    it("closes the tabs inside a deleted folder", async () => {
        const vm = mountExplorer().vm as unknown as ExplorerVm

        vm.confirmRemove([{id: "docs", fileName: "docs", leaf: false}])
        await vm.removeItems()

        expect(deleteFileDirectory).toHaveBeenCalledWith({namespace: "company", path: "docs"})
        expect(closeTab).toHaveBeenCalledWith({path: "docs", folder: true})
    })

    it("closes only the exact tab of a deleted file", async () => {
        const vm = mountExplorer().vm as unknown as ExplorerVm

        vm.confirmRemove([{id: "readme", fileName: "README.md", leaf: true}])
        await vm.removeItems()

        expect(closeTab).toHaveBeenCalledWith({path: "README.md", folder: false})
    })
})
