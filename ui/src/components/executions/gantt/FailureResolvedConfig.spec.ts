import {afterEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import FailureResolvedConfig from "./FailureResolvedConfig.vue"
import en from "../../../translations/en.json"

const mocks = vi.hoisted(() => ({
    renderExpressions: vi.fn(),
}))

vi.mock("@kestra-io/kestra-sdk/expressions", () => ({
    renderExpressions: mocks.renderExpressions,
}))

const i18n = createI18n({legacy: false, locale: "en", fallbackWarn: false, missingWarn: false, messages: {en: en.en}})

const RAW_BLOCK = "id: load_warehouse\ntype: io.kestra.plugin.jdbc.snowflake.Query\nurl: \"{{ secret('SNOWFLAKE_URL') }}\"\nsql: SELECT 1\n"

function mountConfig(props: Partial<{rawBlock: string | undefined; flowLoading: boolean; flowError: boolean}> = {}) {
    return mount(FailureResolvedConfig, {
        props: {
            rawBlock: RAW_BLOCK,
            flowLoading: false,
            flowError: false,
            executionId: "exec-1",
            taskRunId: "tr-1",
            ...props,
        },
        global: {
            plugins: [i18n],
            // KsMarkdown's Shiki highlighter needs a real browser (covered by the Storybook
            // story instead) — it throws in jsdom, so it's stubbed here to isolate the logic
            // this spec actually owns: which content reaches it.
            stubs: {KsMarkdown: {template: "<pre>{{ content }}</pre>", props: ["content"]}},
        },
    })
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
})
