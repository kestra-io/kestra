import {afterEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import FailureResolvedConfig from "./FailureResolvedConfig.vue"
import en from "../../../translations/en.json"

const mocks = vi.hoisted(() => ({
    flow: vi.fn(),
    renderExpressions: vi.fn(),
}))

vi.mock("@kestra-io/kestra-sdk/flows", () => ({
    flow: mocks.flow,
}))
vi.mock("@kestra-io/kestra-sdk/expressions", () => ({
    renderExpressions: mocks.renderExpressions,
}))

const i18n = createI18n({legacy: false, locale: "en", fallbackWarn: false, missingWarn: false, messages: {en: en.en}})

const FLOW_SOURCE = `id: daily_sales_sync
namespace: company.analytics
tasks:
  - id: load_warehouse
    type: io.kestra.plugin.jdbc.snowflake.Query
    url: "{{ secret('SNOWFLAKE_URL') }}"
    sql: SELECT 1
`

function mountConfig() {
    return mount(FailureResolvedConfig, {
        props: {
            namespace: "company.analytics",
            flowId: "daily_sales_sync",
            flowRevision: 3,
            executionId: "exec-1",
            taskRunId: "tr-1",
            taskId: "load_warehouse",
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

    it("should fetch the flow at the execution's own revision, not the latest", async () => {
        mocks.flow.mockResolvedValue({source: FLOW_SOURCE})
        mocks.renderExpressions.mockResolvedValue({rendered: {}})

        mountConfig()
        await flushPromises()

        expect(mocks.flow).toHaveBeenCalledWith(expect.objectContaining({
            namespace: "company.analytics",
            id: "daily_sales_sync",
            revision: 3,
            source: true,
        }))
    })

    it("should render the resolved task block once loaded", async () => {
        mocks.flow.mockResolvedValue({source: FLOW_SOURCE})
        mocks.renderExpressions.mockImplementation(({expressions}: {expressions: string[]}) => Promise.resolve({
            rendered: {[expressions[0]]: "id: load_warehouse\ntype: io.kestra.plugin.jdbc.snowflake.Query\nurl: \"[secret: SNOWFLAKE_URL]\"\nsql: SELECT 1\n"},
        }))

        const wrapper = mountConfig()
        await flushPromises()

        expect(mocks.renderExpressions).toHaveBeenCalledWith(expect.objectContaining({
            executionId: "exec-1",
            taskRunId: "tr-1",
        }))
        expect(wrapper.text()).toContain("[secret: SNOWFLAKE_URL]")
    })

    it("should show an empty state when the task no longer exists in that flow revision", async () => {
        mocks.flow.mockResolvedValue({source: "id: daily_sales_sync\nnamespace: company.analytics\ntasks: []\n"})

        const wrapper = mountConfig()
        await flushPromises()

        expect(mocks.renderExpressions).not.toHaveBeenCalled()
        expect(wrapper.text()).toContain("could not be found")
    })

    it("should show an error state when the render call fails", async () => {
        mocks.flow.mockResolvedValue({source: FLOW_SOURCE})
        mocks.renderExpressions.mockRejectedValue(new Error("network error"))

        const wrapper = mountConfig()
        await flushPromises()

        expect(wrapper.text()).toContain("Could not resolve")
    })
})
