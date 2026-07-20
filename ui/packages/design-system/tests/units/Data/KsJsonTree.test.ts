import {describe, expect, test} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsJsonTree from "../../../src/components/Data/KsJsonTree.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

const VALUE = {
    task: {
        id: "extract",
        attempts: 2,
    },
    labels: ["nightly", "production"],
}

describe("KsJsonTree", () => {
    test("renders compact inline tree by default", () => {
        const wrapper = mount(KsJsonTree, {
            props: {value: VALUE, defaultExpanded: true},
            global: globalConfig,
        })

        expect(wrapper.find(".json-node").exists()).toBe(true)
        expect(wrapper.find(".json-tree-row").exists()).toBe(false)
        expect(wrapper.text()).toContain("task")
    })

    test("renders row mode with line gutter and collapsed previews", async () => {
        const wrapper = mount(KsJsonTree, {
            props: {
                value: VALUE,
                displayMode: "rows",
                showGutter: true,
                defaultExpanded: true,
                previewFormatter: (_value: unknown, {kind, count}: {kind: "array" | "object", count: number}) =>
                    `${count} ${kind === "array" ? "items" : "keys"}`,
            },
            global: globalConfig,
        })

        await wrapper.find(".json-tree-caret").trigger("click")

        expect(wrapper.find(".json-tree-gutter").text()).toBe("1")
        expect(wrapper.text()).toContain("2 keys")
    })

    test("emits selected expression path in row mode", async () => {
        const wrapper = mount(KsJsonTree, {
            props: {
                value: VALUE,
                displayMode: "rows",
                selectable: true,
                basePath: "outputs.extract",
                selectedPath: "outputs.extract.task.id",
                defaultExpanded: true,
            },
            global: globalConfig,
        })

        const idRow = wrapper.findAll(".json-tree-row").find(row => row.text().includes("\"id\""))
        await idRow?.trigger("click")

        expect(wrapper.emitted("select")).toEqual([["outputs.extract.task.id"]])
    })
})
