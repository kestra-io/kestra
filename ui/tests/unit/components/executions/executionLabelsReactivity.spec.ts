import {beforeEach, describe, expect, test, vi} from "vitest"
import {computed, defineComponent, h, reactive} from "vue"
import {mount, flushPromises} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import {createI18n} from "vue-i18n"

import Banner from "../../../../src/components/executions/overview/components/Banner.vue"
import {useExecutionsStore} from "../../../../src/stores/executions"
import type {Execution} from "../../../../src/stores/executions"

// https://github.com/kestra-io/kestra/issues/18766 - "Modifying execution labels
// doesn't reload Overview". This exercises the exact chain the bug report describes:
// SetLabels' save handler -> executionsStore.execution reassignment -> the Overview
// tab's `computed(() => store.execution)` -> the `execution` prop into Banner -> the
// rendered label list - with no manual page refresh in between.

vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({user: {isAllowed: () => true}}),
}))

vi.mock("override/stores/misc", () => ({
    useMiscStore: () => ({configs: {hiddenLabelsPrefixes: []}}),
}))

vi.mock("../../../../src/utils/toast", () => ({
    useToast: () => ({success: vi.fn(), error: vi.fn()}),
}))

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
        KsIconButton: {template: "<button v-bind=\"$attrs\"><slot /></button>"},
        KsButton: {template: "<button v-bind=\"$attrs\"><slot /></button>"},
        RunTimeline: true,
        Duration: true,
        ChangeExecutionStatus: {
            props: ["execution"],
            setup() {
                return {triggerScope}
            },
            template: "<div><slot name=\"trigger\" v-bind=\"triggerScope\" /></div>",
        },
        KsPopover: {
            props: ["visible", "disabled"],
            emits: ["update:visible"],
            template: `
                <div>
                    <div data-test="set-labels-reference" @click="!disabled && $emit('update:visible', true)">
                        <slot name="reference" />
                    </div>
                    <div v-if="visible" data-test="set-labels-body"><slot /></div>
                </div>
            `,
        },
        LabelInput: {
            props: ["labels", "existingLabels"],
            emits: ["update:labels"],
            template: `
                <div>
                    <slot name="header" />
                    <button data-test="add-label" @click="$emit('update:labels', [{key: 'env', value: 'prod'}])">add label</button>
                    <slot name="header-end" />
                </div>
            `,
        },
    },
}

function buildExecution(labels: {key: string; value: string}[] = []): Execution {
    return {
        id: "execution-id",
        originalId: "execution-id",
        namespace: "io.kestra.tests",
        flowId: "flow",
        flowRevision: 1,
        labels,
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

// Mirrors ui/src/components/executions/overview/Overview.vue's own
// `const execution = computed(() => store.execution)` -> `<Banner :execution />` -
// i.e. the exact reactive path between the store and the rendered banner.
const OverviewLike = defineComponent({
    setup() {
        const store = useExecutionsStore()
        const execution = computed(() => store.execution)
        return () => (execution.value ? h(Banner, {execution: execution.value}) : null)
    },
})

describe("execution labels stay in sync with the Overview banner after a save (#18766)", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        triggerScope.visible = false
        triggerScope.enabled = false
    })

    test("reflects a newly-added label immediately, without a manual refresh", async () => {
        const store = useExecutionsStore()
        store.execution = buildExecution([])

        const updatedExecution = buildExecution([{key: "env", value: "prod"}])
        vi.spyOn(store, "setLabels").mockResolvedValue(updatedExecution as any)

        const wrapper = mount(OverviewLike, {global: globalConfig})

        expect(wrapper.text()).not.toContain("env: prod")

        await wrapper.get("[data-test=\"set-labels-reference\"]").trigger("click")
        await wrapper.get("[data-test=\"add-label\"]").trigger("click")

        const saveButton = wrapper.findAll("button").find((btn) => btn.text().toLowerCase() === "save")
        await saveButton?.trigger("click")

        await flushPromises()

        expect(store.setLabels).toHaveBeenCalledWith({
            labels: [{key: "env", value: "prod"}],
            executionId: "execution-id",
        })
        expect(wrapper.text()).toContain("env: prod")
    })
})
