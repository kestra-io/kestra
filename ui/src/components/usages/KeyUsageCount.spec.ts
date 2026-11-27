import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {i18nMount} from "../../../tests/unit/i18nMount"

const {mockSearchFlowsBySourceCode} = vi.hoisted(() => ({mockSearchFlowsBySourceCode: vi.fn()}))

vi.mock("@kestra-io/kestra-sdk/flows", () => ({searchFlowsBySourceCode: mockSearchFlowsBySourceCode}))

import KeyUsageCount from "./KeyUsageCount.vue"
import {resetKeyUsageCounts} from "../../composables/useKeyUsageCount"

const flow = (namespace: string, id: string) => ({namespace, id, editable: true, matches: [{line: 1, column: 0, snippet: "kv('k')"}]})

const mountCount = () => i18nMount(KeyUsageCount, {props: {target: {kind: "kv" as const, namespace: "company", key: "k"}}})

describe("KeyUsageCount", () => {
    beforeEach(() => {
        mockSearchFlowsBySourceCode.mockReset()
        resetKeyUsageCounts()
    })

    it("holds the cell with a skeleton while counting", () => {
        mockSearchFlowsBySourceCode.mockReturnValue(new Promise(() => {}))

        const wrapper = mountCount()

        expect(wrapper.find("[data-test='key-usage-count-loading']").exists()).toBe(true)
    })

    it("shows the flow count and asks to open the details", async () => {
        mockSearchFlowsBySourceCode.mockResolvedValue({results: [flow("company", "a"), flow("company", "b")], total: 2})

        const wrapper = mountCount()
        await flushPromises()

        const button = wrapper.find("[data-test='key-usage-count']")
        expect(button.text()).toContain("key_usages.count")
        await button.trigger("click")
        expect(wrapper.emitted("open")).toHaveLength(1)
    })

    it("says when nothing references the key", async () => {
        mockSearchFlowsBySourceCode.mockResolvedValue({results: [], total: 0})

        const wrapper = mountCount()
        await flushPromises()

        expect(wrapper.find("[data-test='key-usage-count']").text()).toContain("key_usages.count_none")
    })

    it("stays quiet when the count fails", async () => {
        mockSearchFlowsBySourceCode.mockRejectedValue(new Error("boom"))

        const wrapper = mountCount()
        await flushPromises()

        expect(wrapper.find("[data-test='key-usage-count-failed']").exists()).toBe(true)
    })
})
