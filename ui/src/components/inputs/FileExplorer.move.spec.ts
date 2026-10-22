import {beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {i18nShallowMount} from "../../../tests/unit/i18nMount"

const {moveFileDirectory, paths} = vi.hoisted(() => ({
    moveFileDirectory: vi.fn(),
    paths: {} as Record<string, string>,
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

vi.mock("../../stores/fileExplorer", async (importOriginal) => ({
    ...(await importOriginal<typeof import("../../stores/fileExplorer")>()),
    useFileExplorerStore: () => ({
        namespaceId: "company",
        fileTree: [],
        getPath: (id: string) => paths[id],
        loadNodes: vi.fn(),
    }),
}))

import FileExplorer from "./FileExplorer.vue"

const node = {data: {id: "readme"}, parent: {data: {id: "root"}}}

async function dragAndDrop(pathBefore: string, pathAfter: string) {
    const wrapper = i18nShallowMount(FileExplorer, {global: {plugins: [createPinia()]}})
    const tree = wrapper.findComponent({name: "KsTree"})

    paths.readme = pathBefore
    tree.vm.$emit("node-drag-start", node)
    paths.readme = pathAfter
    tree.vm.$emit("node-drop", node)
    await Promise.resolve()
}

describe("FileExplorer drag and drop", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        moveFileDirectory.mockReset().mockResolvedValue(undefined)
    })

    it("does not ask the server to move a file dropped back on its own path", async () => {
        await dragAndDrop("README.md", "README.md")

        expect(moveFileDirectory).not.toHaveBeenCalled()
    })

    it("does not offer a drop that leaves a file in its own folder", () => {
        const wrapper = i18nShallowMount(FileExplorer, {global: {plugins: [createPinia()]}})
        const allowDrop = wrapper.findComponent({name: "KsTree"}).props("allowDrop")
        const root = {data: {id: "root"}}
        const docs = {data: {id: "docs"}, parent: root}
        const readme = {data: {id: "readme", leaf: true}, parent: root}
        const contributing = {data: {id: "contributing", leaf: true}, parent: root}

        expect(allowDrop(readme, contributing, "next")).toBe(false)
        expect(allowDrop(readme, docs, "inner")).toBe(true)
        expect(allowDrop(readme, contributing, "inner")).toBe(false)
    })

    it("moves a file dropped into another folder", async () => {
        await dragAndDrop("README.md", "docs/README.md")

        expect(moveFileDirectory).toHaveBeenCalledWith({namespace: "company", old: "README.md", new: "docs/README.md"})
    })
})
