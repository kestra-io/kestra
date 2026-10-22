import {describe, expect, it, vi} from "vitest"
import {i18nMount} from "../../../../tests/unit/i18nMount"
import FailureStructuralImpact from "./FailureStructuralImpact.vue"
import en from "../../../translations/en.json"

vi.mock("../../../stores/plugins", () => ({
    usePluginsStore: () => ({loadIcon: vi.fn().mockResolvedValue(undefined)}),
}))

function node(id: string, taskId: string, relation: "focused" | "parent" | "sibling" | "child") {
    return {
        taskRun: {id, taskId, state: {current: "SUCCESS", histories: []}},
        relation,
    }
}

function mountImpact(nodes: ReturnType<typeof node>[], rawBlocks: Record<string, string | undefined> = {}) {
    return i18nMount(FailureStructuralImpact, {
        locales: {en: en.en},
        props: {nodes: nodes as never, focusedId: "tr-focused", rawBlocks},
        global: {
            stubs: {KsMarkdown: {template: "<pre>{{ content }}</pre>", props: ["content"]}},
        },
    })
}

describe("FailureStructuralImpact", () => {
    it("should emit focus when a non-focused node is clicked", async () => {
        const wrapper = mountImpact([
            node("tr-focused", "load_warehouse", "focused"),
            node("tr-sibling", "transform_orders", "sibling"),
        ])

        await wrapper.findAll(".structural-node__button")[1].trigger("click")

        expect(wrapper.emitted("focus")).toEqual([["tr-sibling"]])
    })

    it("should not emit focus when the already-focused node is clicked", async () => {
        const wrapper = mountImpact([node("tr-focused", "load_warehouse", "focused")])

        await wrapper.get(".structural-node__button").trigger("click")

        expect(wrapper.emitted("focus")).toBeUndefined()
    })

    it("should not show a view-definition toggle for the focused node, even with a raw block available", () => {
        const wrapper = mountImpact(
            [node("tr-focused", "load_warehouse", "focused")],
            {"tr-focused": "id: load_warehouse\ntype: io.kestra.plugin.core.execution.Fail\n"},
        )

        expect(wrapper.find("[aria-label=\"View task definition\"]").exists()).toBe(false)
    })

    it("should not show a view-definition toggle for a neighbor with no raw block", () => {
        const wrapper = mountImpact([
            node("tr-focused", "load_warehouse", "focused"),
            node("tr-sibling", "transform_orders", "sibling"),
        ])

        expect(wrapper.find("[aria-label=\"View task definition\"]").exists()).toBe(false)
    })

    it("should toggle a neighbor's raw task definition open and closed", async () => {
        const wrapper = mountImpact(
            [
                node("tr-focused", "load_warehouse", "focused"),
                node("tr-sibling", "transform_orders", "sibling"),
            ],
            {"tr-sibling": "id: transform_orders\ntype: io.kestra.plugin.core.log.Log\n"},
        )

        expect(wrapper.find(".structural-node__definition").exists()).toBe(false)

        await wrapper.get("[aria-label=\"View task definition\"]").trigger("click")

        expect(wrapper.get(".structural-node__definition").text()).toContain("transform_orders")
        expect(wrapper.find("[aria-label=\"View task definition\"]").exists()).toBe(false)

        await wrapper.get("[aria-label=\"Hide task definition\"]").trigger("click")

        expect(wrapper.find(".structural-node__definition").exists()).toBe(false)
    })
})
