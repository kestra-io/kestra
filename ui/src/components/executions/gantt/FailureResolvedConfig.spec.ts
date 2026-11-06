import {afterEach, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {i18nMount} from "../../../../tests/unit/i18nMount"
import FailureResolvedConfig from "./FailureResolvedConfig.vue"
import en from "../../../translations/en.json"

const mocks = vi.hoisted(() => ({
    renderExpressions: vi.fn(),
}))

vi.mock("@kestra-io/kestra-sdk/expressions", () => ({
    renderExpressions: mocks.renderExpressions,
}))

const RAW_BLOCK = "id: load_warehouse\ntype: io.kestra.plugin.jdbc.snowflake.Query\nurl: \"{{ secret('SNOWFLAKE_URL') }}\"\nsql: SELECT 1\n"

function mountConfig(props: Partial<{rawBlock: string | undefined; flowLoading: boolean; flowError: boolean}> = {}) {
    return i18nMount(FailureResolvedConfig, {
        locales: {en: en.en},
        props: {
            rawBlock: RAW_BLOCK,
            flowLoading: false,
            flowError: false,
            executionId: "exec-1",
            taskRunId: "tr-1",
            ...props,
        },
        global: {
            stubs: {KsMarkdown: {template: "<pre>{{ content }}</pre>", props: ["content"]}},
        },
    })
}

function deferred<T>() {
    let resolve!: (value: T) => void
    const promise = new Promise<T>((res) => { resolve = res })
    return {promise, resolve}
}

describe("FailureResolvedConfig", () => {
    afterEach(() => {
        vi.clearAllMocks()
    })

    it("should render the resolved task block once loaded", async () => {
        mocks.renderExpressions.mockResolvedValue({
            rendered: {[RAW_BLOCK]: "id: load_warehouse\ntype: io.kestra.plugin.jdbc.snowflake.Query\nurl: \"[secret: SNOWFLAKE_URL]\"\nsql: SELECT 1\n"},
        })

        const wrapper = mountConfig()
        await flushPromises()

        expect(mocks.renderExpressions).toHaveBeenCalledWith(expect.objectContaining({
            expressions: [RAW_BLOCK],
            executionId: "exec-1",
            taskRunId: "tr-1",
        }))
        expect(wrapper.text()).toContain("[secret: SNOWFLAKE_URL]")
    })

    it("should show an empty state when there is no task block to resolve", async () => {
        const wrapper = mountConfig({rawBlock: undefined})
        await flushPromises()

        expect(mocks.renderExpressions).not.toHaveBeenCalled()
        expect(wrapper.text()).toContain("could not be found")
    })

    it("should show a loading state while the shared flow fetch is still in flight", () => {
        const wrapper = mountConfig({flowLoading: true, rawBlock: undefined})

        expect(mocks.renderExpressions).not.toHaveBeenCalled()
        expect(wrapper.find(".resolved-config__status").exists()).toBe(true)
    })

    it("should show an error state when the shared flow fetch failed", async () => {
        const wrapper = mountConfig({flowError: true})
        await flushPromises()

        expect(wrapper.text()).toContain("Could not resolve")
    })

    it("should show an error state when the render call fails", async () => {
        mocks.renderExpressions.mockRejectedValue(new Error("network error"))

        const wrapper = mountConfig()
        await flushPromises()

        expect(wrapper.text()).toContain("Could not resolve")
    })

    it("should keep the selected task run's configuration when an earlier render answers last", async () => {
        const first = deferred<{rendered: Record<string, string>}>()
        const second = deferred<{rendered: Record<string, string>}>()
        mocks.renderExpressions.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)

        const wrapper = mountConfig()
        await wrapper.setProps({taskRunId: "tr-2"})
        second.resolve({rendered: {[RAW_BLOCK]: "resolved for tr-2"}})
        await flushPromises()
        first.resolve({rendered: {[RAW_BLOCK]: "resolved for tr-1"}})
        await flushPromises()

        expect(wrapper.text()).toContain("resolved for tr-2")
        expect(wrapper.text()).not.toContain("resolved for tr-1")
    })
})
