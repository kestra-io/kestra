import {describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import FailureStructuralImpact from "./FailureStructuralImpact.vue"
import en from "../../../translations/en.json"

vi.mock("../../../stores/plugins", () => ({
    usePluginsStore: () => ({loadIcon: vi.fn().mockResolvedValue(undefined)}),
}))

const i18n = createI18n({legacy: false, locale: "en", fallbackWarn: false, missingWarn: false, messages: {en: en.en}})

function node(id: string, taskId: string, relation: "focused" | "parent" | "sibling" | "child") {
    return {
        taskRun: {id, taskId, state: {current: "SUCCESS", histories: []}},
        relation,
    }
}

function mountImpact(nodes: ReturnType<typeof node>[], rawBlocks: Record<string, string | undefined> = {}) {
    return mount(FailureStructuralImpact, {
        props: {nodes: nodes as never, focusedId: "tr-focused", rawBlocks},
        global: {
            plugins: [i18n],
            // KsMarkdown's Shiki highlighting crashes in jsdom (no real browser) — stubbed here,
            // covered for real rendering by the FailureDebugPanel Storybook story instead.
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
