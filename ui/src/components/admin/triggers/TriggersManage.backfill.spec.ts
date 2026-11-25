import {afterEach, describe, expect, it, vi} from "vitest"
import {flushPromises, type VueWrapper} from "@vue/test-utils"
import {createPinia} from "pinia"
import {createMemoryHistory, createRouter} from "vue-router"
import {defineComponent, h} from "vue"
import KestraDesignSystem from "@kestra-io/design-system"
import {i18nMount} from "../../../../tests/unit/i18nMount"

const createBackfill = vi.hoisted(() => vi.fn().mockResolvedValue({}))

vi.mock("@kestra-io/kestra-sdk/logs", () => ({searchLogs: vi.fn().mockResolvedValue({results: [], total: 0}), deleteLogsFromFlow: vi.fn()}))
vi.mock("@kestra-io/kestra-sdk/triggers", async (importOriginal) => ({
    ...await importOriginal<typeof import("@kestra-io/kestra-sdk/triggers")>(),
    createBackfill,
}))

vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({user: {
        isAllowed: () => true,
        hasAny: () => true,
        hasAnyAction: () => true,
    }}),
}))

vi.mock("../../../stores/flow", () => ({
    useFlowStore: () => ({flow: undefined}),
}))

const flowWithDateInputs = {
    namespace: "company.team",
    id: "my-flow",
    inputs: [
        {id: "day", type: "DATE"},
        {id: "at", type: "TIME"},
    ],
}

const executionsStore = vi.hoisted(() => {
    const store: {
        flow: typeof flowWithDateInputs | undefined
        loadFlowForExecution: ReturnType<typeof vi.fn>
    } = {
        flow: undefined,
        loadFlowForExecution: vi.fn().mockImplementation(() => {
            store.flow = flowWithDateInputs
            return Promise.resolve(flowWithDateInputs)
        }),
    }
    return store
})

vi.mock("../../../stores/executions", () => ({
    useExecutionsStore: () => executionsStore,
}))

vi.mock("../../../utils/triggers", async (importOriginal) => ({
    ...await importOriginal<typeof import("../../../utils/triggers")>(),
    searchTriggers: vi.fn().mockResolvedValue({
        total: 1,
        results: [
            {
                trigger: {id: "hourly", type: "io.kestra.plugin.core.trigger.Schedule"},
                state: {namespace: "company.team", flowId: "my-flow", triggerId: "hourly", nextEvaluationDate: "2026-10-27T10:01:00Z"},
            },
        ],
    }),
}))

vi.mock("../../../utils/toast", async (importOriginal) => ({
    ...await importOriginal<typeof import("../../../utils/toast")>(),
    useToast: () => ({saved: vi.fn(), success: vi.fn(), error: vi.fn(), confirm: vi.fn()}),
}))

import TriggersManage from "./TriggersManage.vue"

const FlowRunStub = defineComponent({
    name: "FlowRun",
    emits: ["update-inputs", "update-inputs-no-default", "update-labels"],
    setup: () => () => h("div"),
})

const DatePickerStub = defineComponent({
    name: "KsDatePicker",
    inheritAttrs: false,
    props: {modelValue: {type: Date, default: null}},
    emits: ["update:modelValue"],
    setup: (_, {emit}) => () => h("button", {type: "button", "data-test": "date-picker", onClick: () => emit("update:modelValue", new Date("2026-01-01T00:00:00Z"))}),
})

const mounted: VueWrapper[] = []

afterEach(() => {
    while (mounted.length) mounted.pop()?.unmount()
    document.body.className = ""
    document.body.innerHTML = ""
    executionsStore.flow = undefined
    createBackfill.mockClear()
})

describe("TriggersManage - backfill inputs normalization", () => {
    it("normalizes DATE and TIME inputs when submitting backfill from admin triggers", async () => {
        localStorage.setItem("columns_displayTriggersColumns", "flowId,nextEvaluationDate")
        const router = createRouter({
            history: createMemoryHistory(),
            routes: [{name: "admin/triggers", path: "/:tenant/triggers", component: {template: "<div/>"}}],
        })
        await router.push({name: "admin/triggers", params: {tenant: "main"}})
        await router.isReady()

        const wrapper = i18nMount(TriggersManage, {
            attachTo: document.body,
            global: {
                plugins: [createPinia(), router, KestraDesignSystem],
                stubs: {
                    FlowRun: FlowRunStub,
                    KsDatePicker: DatePickerStub,
                    TopNavBar: true,
                    AddTriggerModal: true,
                    TestEventDialog: true,
                    KsFilter: {template: "<div />", props: ["prefix"]},
                },
            },
        })
        mounted.push(wrapper)
        await flushPromises()

        const openBackfillButton = wrapper.findAll("button").find(button => button.text().includes("backfill executions"))
        await openBackfillButton?.trigger("click")
        await flushPromises()

        expect(executionsStore.loadFlowForExecution).toHaveBeenCalledWith({
            namespace: "company.team",
            flowId: "my-flow",
            store: true,
        })

        document.querySelector<HTMLButtonElement>("[data-test='date-picker']")?.click()
        await flushPromises()

        wrapper.findComponent(FlowRunStub).vm.$emit("update-inputs", {
            day: "2026-01-15",
            at: "2026-01-15T09:30:00",
        })
        await flushPromises()

        const executeButton = [...document.querySelectorAll<HTMLButtonElement>("button")].find(
            button => button.textContent?.trim() === "execute backfill",
        )
        expect(executeButton?.disabled).toBe(false)
        executeButton?.click()
        await flushPromises()

        expect(createBackfill).toHaveBeenCalledWith(expect.objectContaining({
            namespace: "company.team",
            flowId: "my-flow",
            triggerId: "hourly",
            backfill: expect.objectContaining({
                inputs: {
                    day: "2026-01-15",
                    at: "09:30:00",
                },
            }),
        }))
    })
})
