import {describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "@kestra-io/design-system"
import Restart from "./Restart.vue"

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}, params: {}, name: "executions/update"}),
    useRouter: () => ({push: vi.fn(), resolve: vi.fn(() => ({href: ""})), currentRoute: {value: {params: {}}}}),
}))

function mountRestart(state: string) {
    setActivePinia(createPinia())
    return mount(Restart, {
        props: {
            isReplay: true,
            execution: {id: "exec1", namespace: "tests", flowId: "flow1", state: {current: state}},
        },
        global: {
            plugins: [createI18n({legacy: false, locale: "en", missingWarn: false, fallbackWarn: false, messages: {en: {}}}), KestraDesignSystem],
        },
    })
}

describe("Restart (replay)", () => {
    it.each([
        // QUEUED and RETRYING are not running but not terminated either: the API rejects a
        // replay of either with a 409, so the button must stay disabled for them too.
        ["QUEUED", true],
        ["RETRYING", true],
        ["RUNNING", true],
        ["SUCCESS", false],
        ["FAILED", false],
        ["KILLED", false],
    ])("disables replay when execution state is %s: disabled=%s", (state, expectDisabled) => {
        const wrapper = mountRestart(state)

        expect(wrapper.find("button").attributes("disabled") !== undefined).toBe(expectDisabled)
    })
})
