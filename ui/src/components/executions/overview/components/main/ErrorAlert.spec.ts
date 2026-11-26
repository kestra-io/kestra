import {afterEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "@kestra-io/design-system"
import ErrorAlert from "./ErrorAlert.vue"
import {useExecutionsStore, type Execution} from "../../../../../stores/executions"

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}, params: {}, name: "executions/update"}),
    useRouter: () => ({push: vi.fn(), resolve: vi.fn(() => ({href: ""})), currentRoute: {value: {params: {}}}}),
}))

describe("ErrorAlert", () => {
    afterEach(() => localStorage.clear())

    it("quotes the error of the task that failed first rather than a later error handler's log", async () => {
        setActivePinia(createPinia())
        const failed = (id: string, date: string) => ({id, state: {current: "FAILED", histories: [{state: "RUNNING", date: "2026-10-14T11:55:00Z"}, {state: "FAILED", date}]}})
        const execution = {
            id: "e1",
            namespace: "ns",
            flowId: "flow",
            kind: "NORMAL",
            state: {current: "FAILED"},
            taskRunList: [
                failed("parent", "2026-10-14T11:55:03Z"),
                failed("boom", "2026-10-14T11:55:01Z"),
                {id: "on_error", state: {current: "SUCCESS", histories: [{state: "SUCCESS", date: "2026-10-14T11:55:02Z"}]}},
            ],
        } as unknown as Execution
        vi.spyOn(useExecutionsStore(), "loadLogs").mockImplementation(async ({params}) =>
            (params?.["filters[taskRunId][EQUALS]"] === "boom"
                ? [{level: "ERROR", taskId: "boom", message: "The real reason it failed"}]
                : [{level: "ERROR", taskId: "on_error", message: "error branch ran"}]) as never,
        )

        const wrapper = mount(ErrorAlert, {
            props: {execution},
            global: {
                plugins: [createI18n({legacy: false, locale: "en", missingWarn: false, fallbackWarn: false, messages: {en: {}}}), KestraDesignSystem],
                stubs: {LogLine: true, RouterLink: true},
            },
        })
        await flushPromises()

        expect(wrapper.text()).toContain("The real reason it failed")
        expect(wrapper.text()).not.toContain("error branch ran")
    })
})
