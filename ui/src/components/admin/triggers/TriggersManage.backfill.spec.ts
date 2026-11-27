import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {defineComponent, h} from "vue"
import {flushPromises, type VueWrapper} from "@vue/test-utils"
import {createPinia} from "pinia"
import {createMemoryHistory, createRouter} from "vue-router"
import {i18nMount} from "../../../../tests/unit/i18nMount"
import {normalize} from "../../../utils/inputs"

const createBackfill = vi.hoisted(() => vi.fn())

vi.mock("@kestra-io/kestra-sdk/triggers", async (importOriginal) => ({
    ...await importOriginal<typeof import("@kestra-io/kestra-sdk/triggers")>(),
    createBackfill,
}))
vi.mock("@kestra-io/kestra-sdk/logs", () => ({searchLogs: vi.fn().mockResolvedValue({results: [], total: 0}), deleteLogsFromFlow: vi.fn()}))
vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({user: {isAllowed: () => true, hasAny: () => true, hasAnyAction: () => true}}),
}))
vi.mock("../../../utils/toast", () => ({
    useToast: () => ({saved: vi.fn(), success: vi.fn(), error: vi.fn(), confirm: vi.fn()}),
}))
vi.mock("../../../stores/flow", () => ({useFlowStore: () => ({})}))
const executionsStore = vi.hoisted(() => ({
    flow: undefined as {inputs: Record<string, unknown>[]} | undefined,
    loadFlowForExecution: vi.fn(),
}))
vi.mock("../../../stores/executions", () => ({useExecutionsStore: () => executionsStore}))
vi.mock("../../../utils/triggers", async (importOriginal) => ({
    ...await importOriginal<typeof import("../../../utils/triggers")>(),
    searchTriggers: vi.fn().mockResolvedValue({
        total: 1,
        results: [{
            trigger: {id: "hourly", type: "io.kestra.plugin.core.trigger.Schedule"},
            state: {namespace: "company.team", flowId: "daily_report", triggerId: "hourly"},
        }],
    }),
}))

import TriggersManage from "./TriggersManage.vue"

const flowInputs = [
    {id: "day", type: "DATE", required: false, defaults: "2026-01-15"},
    {id: "at", type: "TIME", required: false, defaults: "09:30:00"},
]

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

beforeEach(() => {
    vi.useFakeTimers()
    localStorage.clear()
    sessionStorage.clear()
    createBackfill.mockReset().mockResolvedValue({})
    executionsStore.flow = undefined
    executionsStore.loadFlowForExecution.mockReset().mockImplementation(() => {
        executionsStore.flow = {inputs: flowInputs}
        return Promise.resolve(executionsStore.flow)
    })
})

afterEach(() => {
    while (mounted.length) mounted.pop()?.unmount()
    localStorage.clear()
    sessionStorage.clear()
    document.title = ""
    document.body.className = ""
    document.body.innerHTML = ""
    vi.useRealTimers()
})

async function openBackfill() {
    localStorage.setItem("columns_displayTriggersColumns", "flowId,backfill")
    const router = createRouter({
        history: createMemoryHistory(),
        routes: [{name: "admin/triggers", path: "/:tenant/triggers", component: {template: "<div/>"}}],
    })
    await router.push({name: "admin/triggers", params: {tenant: "main"}})
    await router.isReady()
    const wrapper = i18nMount(TriggersManage, {
        attachTo: document.body,
        global: {
            plugins: [createPinia(), router],
            stubs: {FlowRun: FlowRunStub, KsDatePicker: DatePickerStub, TopNavBar: true, AddTriggerModal: true, TestEventDialog: true},
        },
    })
    mounted.push(wrapper)
    await flushPromises()

    const open = wrapper.findAll("button").find(button => button.text() === "backfill executions")
    await open?.trigger("click")
    await flushPromises()

    document.querySelector<HTMLButtonElement>("[data-test='date-picker']")?.click()
    await flushPromises()
    return wrapper
}

const executeButton = () =>
    [...document.querySelectorAll<HTMLButtonElement>("button")].find(button => button.textContent?.trim() === "execute backfill")

// https://github.com/kestra-io/kestra/issues/22959
describe("TriggersManage backfill inputs", () => {
    it("sends DATE and TIME defaults in the format Execute sends", async () => {
        const wrapper = await openBackfill()

        wrapper.findComponent(FlowRunStub).vm.$emit("update-inputs", {day: normalize("DATE", "2026-01-15"), at: normalize("TIME", "09:30:00")})
        await flushPromises()
        executeButton()?.click()
        await flushPromises()

        expect(createBackfill).toHaveBeenCalledTimes(1)
        expect(createBackfill.mock.calls[0][0]).toMatchObject({namespace: "company.team", flowId: "daily_report", triggerId: "hourly"})
        expect(createBackfill.mock.calls[0][0].backfill.inputs).toEqual({day: "2026-01-15", at: "09:30:00"})
    })
})
