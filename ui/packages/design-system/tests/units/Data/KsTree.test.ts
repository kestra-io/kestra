import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsTree from "../../../src/components/Data/KsTree.vue"

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
        expect(typeof (wrapper.vm as any).getNode).toBe("function")
    })

    test("exposes getCheckedNodes method", () => {
        const wrapper = mount(KsTree, {
            props: {data: TREE_DATA},
            global: globalConfig,
        })
        expect(typeof (wrapper.vm as any).getCheckedNodes).toBe("function")
    })

    test("exposes setCurrentKey method", () => {
        const wrapper = mount(KsTree, {
            props: {data: TREE_DATA},
            global: globalConfig,
        })
        expect(typeof (wrapper.vm as any).setCurrentKey).toBe("function")
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
})
