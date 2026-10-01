import {beforeEach, describe, expect, test, vi} from "vitest"
import {reactive} from "vue"
import {mount, flushPromises} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import {createI18n} from "vue-i18n"

import Overview from "../../../../src/components/executions/overview/Overview.vue"
import {useExecutionsStore} from "../../../../src/stores/executions"
import type {Execution} from "../../../../src/stores/executions"

// https://github.com/kestra-io/kestra/issues/18766 - "Modifying execution labels
// doesn't reload Overview". Drives the real Overview.vue: SetLabels' save handler ->
// the executions store -> Overview's Banner -> the rendered label list, with no
// manual page refresh in between.

vi.mock("vue-router", async (importOriginal) => ({
    ...await importOriginal<typeof import("vue-router")>(),
    useRoute: () => ({params: {id: "execution-id"}, query: {}}),
}))

// The graph, the error alert and the prev/next navigation are not part of this chain.
vi.mock("../../../../src/components/executions/Topology.vue", () => ({default: {template: "<div />"}}))
vi.mock("../../../../src/components/executions/overview/components/main/ErrorAlert.vue", () => ({default: {template: "<div />"}}))
vi.mock("../../../../src/components/executions/overview/components/main/PrevNext.vue", () => ({default: {template: "<div />"}}))

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
        KsCard: {template: "<div><slot /></div>"},
        KsNoData: true,
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
        vi.spyOn(store, "setLabels").mockResolvedValue(
            updatedExecution as unknown as Awaited<ReturnType<typeof store.setLabels>>,
        )
        // The bug this test guards against wasn't the label save itself - it was
        // SetLabels.vue writing the response straight to `executionsStore.execution`
        // instead of through this action, which is what keeps an SSE snapshot sent
        // before the save from reverting it (see
        // stores/executions.ts's applySavedExecution). Asserting on this call is
        // what actually distinguishes "goes through the guarded path" from "happens to
        // render the same text" - reverting SetLabels.vue to the raw assignment fails
        // only this expectation, not the render assertion below.
        const applySavedExecutionSpy = vi.spyOn(store, "applySavedExecution")

        const wrapper = mount(Overview, {global: globalConfig})

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
        expect(applySavedExecutionSpy).toHaveBeenCalledWith(updatedExecution)
        expect(wrapper.text()).toContain("env: prod")
    })
})
