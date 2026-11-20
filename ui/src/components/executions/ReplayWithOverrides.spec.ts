import {afterAll, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import {i18nMount} from "../../../tests/unit/i18nMount"
import en from "../../translations/en.json"
import {useFlowStore} from "../../stores/flow"
import ReplayWithOverrides from "./ReplayWithOverrides.vue"
import type {ExecutionTaskRun} from "./executionTaskRun"

const {taskRunOutputs, replayExecutionWithOverrides} = vi.hoisted(() => ({
    taskRunOutputs: vi.fn(),
    replayExecutionWithOverrides: vi.fn(),
}))

vi.mock("@kestra-io/kestra-sdk/executions", () => ({replayExecutionWithOverrides}))
vi.mock("@kestra-io/kestra-sdk/outputs", () => ({taskRunOutputs}))

vi.mock("vue-router", async (importOriginal) => ({
    ...await importOriginal<typeof import("vue-router")>(),
    useRouter: () => ({resolve: vi.fn(() => ({href: ""})), currentRoute: {value: {params: {}}}}),
}))

vi.mock("../../utils/toast", () => {
    const toast = () => ({success: vi.fn(), error: vi.fn(), confirm: vi.fn()})
    return {useToast: toast, makeToast: toast}
})

const location = window.location
afterAll(() => Object.defineProperty(window, "location", {value: location}))

const state = (current: "SUCCESS" | "FAILED", startDate: string, endDate: string) => ({current, histories: [], startDate, endDate})

const taskRunList: ExecutionTaskRun[] = [
    {id: "tr-fetch", taskId: "fetch_store", state: state("SUCCESS", "2026-01-01T10:00:00Z", "2026-01-01T10:00:05Z")},
    {id: "tr-save", taskId: "save_stock", state: state("FAILED", "2026-01-01T10:00:06Z", "2026-01-01T10:00:07Z"), attempts: [{state: {current: "FAILED", histories: []}}]},
]

const flow = {
    tasks: [
        {id: "fetch_store", type: "io.kestra.plugin.core.output.OutputValues"},
        {
            id: "save_stock",
            type: "io.kestra.plugin.core.execution.Fail",
            condition: "{{ outputs.fetch_store.values.store_code != 'KR-0412' }}",
            errorMessage: "{{ outputs.fetch_store.values.qty_delta }} {{ outputs.later.value }}",
        },
    ],
}

async function openDialog() {
    Object.defineProperty(window, "location", {value: {href: ""}, writable: true})
    setActivePinia(createPinia())
    const flowStore = useFlowStore()
    flowStore.loadFlow = vi.fn().mockResolvedValue(flow) as typeof flowStore.loadFlow
    taskRunOutputs.mockResolvedValue({values: {store_code: "KR-0412 ", qty_delta: -12}})
    replayExecutionWithOverrides.mockResolvedValue({id: "replayed", namespace: "company.stores", flowId: "inventory_batch"})

    const wrapper = i18nMount(ReplayWithOverrides, {
        locales: en,
        props: {
            execution: {id: "exec1", namespace: "company.stores", flowId: "inventory_batch", flowRevision: 3, state: {current: "FAILED", histories: []}, taskRunList},
            taskRun: taskRunList[1],
            attemptIndex: 0,
        },
        global: {
            stubs: {
                KsDropdownItem: {template: "<button><slot /></button>"},
                KsDialog: {template: "<div><slot /><slot name='footer' /></div>"},
            },
        },
    })
    await wrapper.find("[data-test='replay-with-overrides']").trigger("click")
    await flushPromises()
    return {wrapper, flowStore}
}

describe("ReplayWithOverrides", () => {
    it("lists the outputs of previous tasks that the task reads, on the execution's revision", async () => {
        const {wrapper, flowStore} = await openDialog()

        expect(flowStore.loadFlow).toHaveBeenCalledWith(expect.objectContaining({revision: "3"}))
        expect(wrapper.findAll("[data-test^='replay-override-outputs'] code").map(code => code.text())).toEqual([
            "outputs.fetch_store.values.store_code",
            "outputs.fetch_store.values.qty_delta",
        ])
        expect((wrapper.find("input[aria-label='outputs.fetch_store.values.store_code']").element as HTMLInputElement).value).toBe("KR-0412 ")
    })

    it("replays with the whole outputs of the task run, the changed value replaced", async () => {
        const {wrapper} = await openDialog()
        expect(wrapper.find("[data-test='replay-confirm']").attributes("disabled")).toBeDefined()

        await wrapper.find("input[aria-label='outputs.fetch_store.values.store_code']").setValue("KR-0412")
        await wrapper.find("[data-test='replay-confirm']").trigger("click")

        expect(replayExecutionWithOverrides).toHaveBeenCalledWith({
            executionId: "exec1",
            taskRunId: "tr-save",
            outputs: {"tr-fetch": {values: {store_code: "KR-0412", qty_delta: -12}}},
        })
    })

    it("blocks the replay while a value does not match the type of the current one", async () => {
        const {wrapper} = await openDialog()

        await wrapper.find("input[aria-label='outputs.fetch_store.values.qty_delta']").setValue("five")

        expect(wrapper.find("[data-test='replay-override-error']").text()).toBe("Enter a number.")
        expect(wrapper.find("[data-test='replay-confirm']").attributes("disabled")).toBeDefined()
    })
})
