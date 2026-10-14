import {afterEach, describe, expect, it, vi} from "vitest"
import {mount, type VueWrapper} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "@kestra-io/design-system"
import Restart from "./Restart.vue"

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}, params: {}, name: "executions/update"}),
    useRouter: () => ({push: vi.fn(), resolve: vi.fn(() => ({href: ""})), currentRoute: {value: {params: {}}}}),
}))

const mounted: VueWrapper[] = []

afterEach(() => mounted.splice(0).forEach((wrapper) => wrapper.unmount()))

function mountRestart(state: string) {
    setActivePinia(createPinia())
    const wrapper = mount(Restart, {
        props: {
            isReplay: true,
            execution: {id: "exec1", namespace: "tests", flowId: "flow1", state: {current: state}},
        },
        global: {
            plugins: [createI18n({legacy: false, locale: "en", missingWarn: false, fallbackWarn: false, messages: {en: {}}}), KestraDesignSystem],
        },
    })
    mounted.push(wrapper)
    return wrapper
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

describe("Restart (subflow child)", () => {
    const messages = {en: {"restart subflow warning": "Created by a {parent}.", "restart subflow warning link": "parent execution"}}

    function mountChild(trigger: Record<string, unknown> | undefined) {
        setActivePinia(createPinia())
        const wrapper = mount(Restart, {
            props: {
                execution: {id: "child1", namespace: "tests", flowId: "child-flow", state: {current: "FAILED"}, trigger},
            },
            global: {
                plugins: [createI18n({legacy: false, locale: "en", missingWarn: false, fallbackWarn: false, messages}), KestraDesignSystem],
                stubs: {KsDialog: {template: "<div><slot /></div>"}, RouterLink: {props: ["to"], template: "<a data-test='parent-link'><slot /></a>"}},
            },
        })
        mounted.push(wrapper)
        return wrapper
    }

    async function openDialog(wrapper: VueWrapper) {
        await wrapper.find("button").trigger("click")
    }

    it("shouldWarnAboutTheParentWhenExecutionWasCreatedBySubflowTask", async () => {
        const wrapper = mountChild({
            type: "io.kestra.plugin.core.flow.Subflow",
            variables: {executionId: "parent1", namespace: "tests", flowId: "parent-flow"},
        })

        await openDialog(wrapper)

        expect(wrapper.find("[data-test='restart-subflow-warning']").text()).toContain("Created by a")
        expect(wrapper.find("[data-test='parent-link']").text()).toBe("parent execution")
        expect(wrapper.findAll("button").at(-1)!.attributes("disabled")).toBeUndefined()
    })

    it.each([
        ["without a trigger", undefined],
        ["from a schedule", {type: "io.kestra.plugin.core.trigger.Schedule", variables: {}}],
    ])("shouldNotWarnWhenExecutionIsCreated %s", async (_name, trigger) => {
        const wrapper = mountChild(trigger)

        await openDialog(wrapper)

        expect(wrapper.find("[data-test='restart-subflow-warning']").exists()).toBe(false)
    })
})
