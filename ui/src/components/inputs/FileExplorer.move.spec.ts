import {beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {defineComponent, h} from "vue"
import {createI18n} from "vue-i18n"
import {flushPromises, shallowMount} from "@vue/test-utils"
import KestraDesignSystem from "@kestra-io/design-system"

type Node = {data: {id: string; leaf?: boolean}; parent?: Node}

const {moveFileDirectory, store} = vi.hoisted(() => ({
    moveFileDirectory: vi.fn(),
    store: {fileTree: [] as unknown[], paths: {} as Record<string, string>},
}))

vi.mock("vue-router", async (importOriginal) => ({
    ...(await importOriginal<typeof import("vue-router")>()),
    useRoute: () => ({params: {namespace: "company"}, query: {}}),
}))

vi.mock("override/stores/namespaces", () => ({
    useNamespacesStore: () => ({moveFileDirectory}),
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

import FileExplorer from "./FileExplorer.vue"

const root: Node = {data: {id: "root"}}
const docs: Node = {data: {id: "docs"}, parent: root}
const readme: Node = {data: {id: "readme", leaf: true}, parent: root}
const changelog: Node = {data: {id: "changelog", leaf: true}, parent: root}
const contributing: Node = {data: {id: "contributing", leaf: true}, parent: docs}

let rows: Node[] = []

const KsTree = defineComponent({
    name: "KsTree",
    props: {allowDrop: {type: Function, default: undefined}},
    emits: ["node-drag-start", "node-drop"],
    methods: {setCurrentKey() {}, remove() {}, append() {}},
    render() {
        return h("div", rows.map((node) => h("div", {key: node.data.id}, this.$slots.default?.({node, data: node.data}))))
    },
})

function mountExplorer() {
    const i18n = createI18n({legacy: false, locale: "en", messages: {en: {}}, missingWarn: false, fallbackWarn: false})
    return shallowMount(FileExplorer, {
        global: {plugins: [createPinia(), i18n, KestraDesignSystem], stubs: {KsTree, KsDropdown: {template: "<div><slot /></div>"}}},
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
    })
})
