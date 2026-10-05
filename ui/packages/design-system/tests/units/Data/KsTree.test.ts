import {describe, test, expect} from "vitest"
import {h} from "vue"
import {mount} from "@vue/test-utils"
import {type ComponentExposed} from "vue-component-type-helpers"
import KestraDesignSystem from "../../../src/index"
import KsTree from "../../../src/components/Data/KsTree.vue"

type KsTreeExposed = ComponentExposed<typeof KsTree>

const globalConfig = {plugins: [KestraDesignSystem]}

const TREE_DATA = [
    {label: "root", children: [{label: "child1"}, {label: "child2"}]},
]

describe("KsTree", () => {
    test("renders tree element", () => {
        const wrapper = mount(KsTree, {
            props: {data: TREE_DATA, props: {label: "label", children: "children"}},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-tree").exists()).toBe(true)
    })

    test("exposes getNode method", () => {
        const wrapper = mount(KsTree, {
            props: {data: TREE_DATA},
            global: globalConfig,
        })
        expect(typeof (wrapper.vm as KsTreeExposed).getNode).toBe("function")
    })

    test("exposes getCheckedNodes method", () => {
        const wrapper = mount(KsTree, {
            props: {data: TREE_DATA},
            global: globalConfig,
        })
        expect(typeof (wrapper.vm as KsTreeExposed).getCheckedNodes).toBe("function")
    })

    test("exposes setCurrentKey method", () => {
        const wrapper = mount(KsTree, {
            props: {data: TREE_DATA},
            global: globalConfig,
        })
        expect(typeof (wrapper.vm as KsTreeExposed).setCurrentKey).toBe("function")
    })

    test("renders tree nodes with default-expand-all", () => {
        const wrapper = mount(KsTree, {
            props: {
                data: TREE_DATA,
                props: {label: "label", children: "children"},
                defaultExpandAll: true,
            },
            global: globalConfig,
        })
        expect(wrapper.find(".kel-tree-node").exists()).toBe(true)
    })

    test("hands loaded items to the slot and to nodeClick", async () => {
        const file = {label: "file.txt", leaf: true}
        const wrapper = mount(KsTree, {
            props: {
                lazy: true,
                props: {label: "label", isLeaf: "leaf"},
                load: (_node: unknown, resolve: (data: typeof file[]) => void) => resolve([file]),
            },
            slots: {default: ({data}: {data: Record<string | number | symbol, unknown>}) => h("span", {class: "item"}, String(data.label))},
            global: globalConfig,
        })
        const item = wrapper.find(".item")
        expect(item.text()).toBe("file.txt")
        await item.trigger("click")
        expect(wrapper.emitted("nodeClick")?.[0][0]).toEqual(file)
    })
})
