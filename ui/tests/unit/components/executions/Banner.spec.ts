import {beforeEach, describe, expect, test} from "vitest"
import {reactive} from "vue"
import {mount} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import {createI18n} from "vue-i18n"

import ChevronDown from "vue-material-design-icons/ChevronDown.vue"
import ChevronUp from "vue-material-design-icons/ChevronUp.vue"

import Banner from "../../../../src/components/executions/overview/components/Banner.vue"
import type {Execution} from "../../../../src/stores/executions"

const i18n = createI18n({
    legacy: false,
    locale: "en",
    missingWarn: false,
    fallbackWarn: false,
})

const triggerScope = reactive({visible: false, enabled: false})

const globalConfig = {
    plugins: [i18n],
    stubs: {
        RouterLink: {props: ["to"], template: "<a><slot /></a>"},
        KsTooltip: {template: "<div><slot /></div>"},
        KsIconButton: {template: "<button><slot /></button>"},
        SetLabels: true,
        RunTimeline: true,
        Duration: true,
        ChangeExecutionStatus: {
            props: ["execution"],
            setup() {
                return {triggerScope}
            },
            template: "<div><slot name=\"trigger\" v-bind=\"triggerScope\" /></div>",
        },
    },
}

function buildExecution(): Execution {
    return {
        id: "execution-id",
        originalId: "execution-id",
        namespace: "io.kestra.tests",
        flowId: "flow",
        flowRevision: 1,
        labels: [],
        taskRunList: [],
        metadata: {
            originalCreatedDate: "2026-01-01T00:00:00Z",
            attemptNumber: 1,
        },
        state: {
            current: "SUCCESS",
            histories: [],
            getStartDate: "2026-01-01T00:00:00Z",
            getEndDate: "",
            getDuration: "PT1S",
        },
    } as Execution
}

function mountBanner() {
    return mount(Banner, {
        props: {execution: buildExecution()},
        global: globalConfig,
    })
}

describe("Banner", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        triggerScope.visible = false
        triggerScope.enabled = false
    })

    test("renders no chevron when the state change trigger is disabled", () => {
        triggerScope.enabled = false
        const wrapper = mountBanner()

        expect(wrapper.findComponent(ChevronDown).exists()).toBe(false)
        expect(wrapper.findComponent(ChevronUp).exists()).toBe(false)
    })

    test("renders the down chevron when enabled and the popover is closed", () => {
        triggerScope.enabled = true
        triggerScope.visible = false
        const wrapper = mountBanner()

        expect(wrapper.findComponent(ChevronDown).exists()).toBe(true)
        expect(wrapper.findComponent(ChevronUp).exists()).toBe(false)
    })

    test("renders the up chevron when enabled and the popover is open", () => {
        triggerScope.enabled = true
        triggerScope.visible = true
        const wrapper = mountBanner()

        expect(wrapper.findComponent(ChevronUp).exists()).toBe(true)
        expect(wrapper.findComponent(ChevronDown).exists()).toBe(false)
    })
})
