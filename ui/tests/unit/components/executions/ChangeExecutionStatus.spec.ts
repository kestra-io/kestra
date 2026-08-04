import {beforeEach, describe, expect, test, vi} from "vitest"
import {h} from "vue"
import {mount} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import {createI18n} from "vue-i18n"

import ChangeExecutionStatus from "../../../../src/components/executions/ChangeExecutionStatus.vue"
import type {Execution} from "../../../../src/stores/executions"

const isAllowedMock = vi.fn().mockReturnValue(true)

vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({user: {isAllowed: (...args: unknown[]) => isAllowedMock(...args)}}),
}))

vi.mock("../../../../src/utils/toast", () => ({
    useToast: () => ({success: vi.fn(), error: vi.fn()}),
}))

const i18n = createI18n({
    legacy: false,
    locale: "en",
    missingWarn: false,
    fallbackWarn: false,
    messages: {
        en: {
            "change state": "Change state",
            "actual state": "Actual state",
            "select a state": "Select a state",
            "close": "Close",
            "are you sure change state": "Are you sure?",
            "cancel": "Cancel",
            "yes": "Yes",
        },
    },
})

const globalConfig = {
    plugins: [i18n],
    stubs: {
        KsPopover: {
            props: ["visible", "disabled"],
            emits: ["update:visible"],
            template: `
                <div>
                    <div data-test="reference" @click="!disabled && $emit('update:visible', true)">
                        <slot name="reference" />
                    </div>
                    <div v-if="visible" data-test="body"><slot /></div>
                </div>
            `,
        },
        KsButton: {template: "<button v-bind=\"$attrs\"><slot /></button>"},
        KsIconButton: {template: "<button v-bind=\"$attrs\"><slot /></button>"},
        KsSelect: {
            props: ["modelValue"],
            template: "<div><slot name=\"label\" :value=\"modelValue\" /><slot /></div>",
        },
        KsOption: {
            props: ["value", "label"],
            template: "<div data-test=\"option\" :data-value=\"value\"><slot /></div>",
        },
        KsExecutionStatus: {
            props: ["status"],
            template: "<span><slot name=\"title\">{{ status }}</slot></span>",
        },
    },
}

function buildExecution(state: string): Execution {
    return {
        id: "execution-id",
        originalId: "execution-id",
        namespace: "io.kestra.tests",
        flowId: "flow",
        flowRevision: 1,
        metadata: {
            originalCreatedDate: "2026-01-01T00:00:00Z",
            attemptNumber: 1,
        },
        state: {
            current: state as Execution["state"]["current"],
            histories: [],
            getStartDate: "2026-01-01T00:00:00Z",
            getEndDate: "",
            getDuration: "PT1S",
        },
    } as Execution
}

function mountChangeExecutionStatus(state: string) {
    return mount(ChangeExecutionStatus, {
        props: {execution: buildExecution(state)},
        slots: {
            trigger: (scope: {visible: boolean; enabled: boolean}) => h(
                "button",
                {"data-test": "trigger", disabled: !scope.enabled},
                String(scope.enabled),
            ),
        },
        global: globalConfig,
    })
}

const RUNNING_FAMILY_STATES = ["SUBMITTED", "CREATED", "RUNNING", "KILLING", "PAUSED", "BREAKPOINT"]
const FALSE_AFFORDANCE_STATES = ["KILLED", "RESTARTED", "QUEUED", "RETRYING"]
const CHANGEABLE_STATES = ["FAILED", "WARNING", "SUCCESS", "CANCELLED", "RETRIED", "SKIPPED"]

describe("ChangeExecutionStatus", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        isAllowedMock.mockReturnValue(true)
    })

    test.each(RUNNING_FAMILY_STATES)(
        "disables the change-state affordance and blocks the popover for a running-family state (%s)",
        async (state) => {
            const wrapper = mountChangeExecutionStatus(state)

            const trigger = wrapper.get("[data-test=\"trigger\"]")
            expect(trigger.text()).toBe("false")
            expect(trigger.attributes("disabled")).toBeDefined()

            await trigger.trigger("click")
            expect(wrapper.find("[data-test=\"body\"]").exists()).toBe(false)
        },
    )

    test.each(FALSE_AFFORDANCE_STATES)(
        "no longer offers a state change for a terminated-but-not-changeable state (%s)",
        async (state) => {
            const wrapper = mountChangeExecutionStatus(state)

            const trigger = wrapper.get("[data-test=\"trigger\"]")
            expect(trigger.text()).toBe("false")

            await trigger.trigger("click")
            expect(wrapper.find("[data-test=\"body\"]").exists()).toBe(false)
        },
    )

    test.each(CHANGEABLE_STATES.map((state) => ({state})))(
        "enables the change-state affordance for a changeable state with permission ($state)",
        async ({state}) => {
            const wrapper = mountChangeExecutionStatus(state)

            const trigger = wrapper.get("[data-test=\"trigger\"]")
            expect(trigger.text()).toBe("true")
            expect(trigger.attributes("disabled")).toBeUndefined()

            await trigger.trigger("click")

            expect(wrapper.find("[data-test=\"body\"]").exists()).toBe(true)

            const offeredStates = wrapper
                .findAll("[data-test=\"option\"]")
                .map((option) => option.attributes("data-value"))

            expect(offeredStates).not.toContain(state)
            expect(offeredStates).not.toContain("RUNNING")
        },
    )

    test("disables the change-state affordance for a changeable state without permission", async () => {
        isAllowedMock.mockReturnValue(false)
        const wrapper = mountChangeExecutionStatus("FAILED")

        const trigger = wrapper.get("[data-test=\"trigger\"]")
        expect(trigger.text()).toBe("false")

        await trigger.trigger("click")
        expect(wrapper.find("[data-test=\"body\"]").exists()).toBe(false)
    })
})
