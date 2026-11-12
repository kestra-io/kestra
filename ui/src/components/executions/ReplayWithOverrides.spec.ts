import {afterAll, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import {i18nMount} from "../../../tests/unit/i18nMount"
import en from "../../translations/en.json"
import {useExecutionsStore} from "../../stores/executions"
import {useFlowStore} from "../../stores/flow"
import ReplayWithOverrides from "./ReplayWithOverrides.vue"

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

const flowSource = `id: inventory_batch
namespace: company.stores
tasks:
  - id: save_stock
    type: io.kestra.plugin.core.kv.Set
    key: "stock_{{ outputs.fetch_store.values.store_code }}"
    value: "{{ outputs.fetch_store.values.qty_delta }}"
`

async function openDialog() {
    Object.defineProperty(window, "location", {value: {href: ""}, writable: true})
    setActivePinia(createPinia())
    const executionsStore = useExecutionsStore()
    const flowStore = useFlowStore()
    flowStore.loadFlow = vi.fn().mockResolvedValue({source: flowSource}) as typeof flowStore.loadFlow
    executionsStore.loadReplayOutputs = vi.fn().mockResolvedValue([
        {path: "outputs.fetch_store.values.store_code", value: "KR-0412 "},
        {path: "outputs.fetch_store.values.qty_delta", value: -12},
    ]) as typeof executionsStore.loadReplayOutputs
    executionsStore.replayExecutionWithOverrides = vi.fn().mockResolvedValue({id: "replayed", namespace: "company.stores", flowId: "inventory_batch"}) as typeof executionsStore.replayExecutionWithOverrides

    const wrapper = i18nMount(ReplayWithOverrides, {
        locales: en,
        props: {
            execution: {id: "exec1", namespace: "company.stores", flowId: "inventory_batch", flowRevision: 3, state: {current: "FAILED", histories: []}},
            taskRun: {id: "tr1", taskId: "save_stock", state: {current: "FAILED", histories: []}, attempts: [{state: {current: "FAILED", histories: []}}]},
            attemptIndex: 0,
        },
        global: {
            stubs: {
                KsDropdownItem: {template: "<button><slot /></button>"},
                KsDialog: {template: "<div><slot /><slot name='footer' /></div>"},
                KsEditor: {props: ["modelValue"], template: "<pre>{{ modelValue }}</pre>"},
            },
        },
    })
    await wrapper.find("[data-test='replay-with-overrides']").trigger("click")
    await flushPromises()
    return {wrapper, executionsStore, flowStore}
}

describe("ReplayWithOverrides", () => {
    it("shows the task as the execution's revision defines it and replays with only the changed outputs", async () => {
        const {wrapper, executionsStore, flowStore} = await openDialog()

        expect(flowStore.loadFlow).toHaveBeenCalledWith(expect.objectContaining({revision: "3"}))
        expect(wrapper.find("[data-test='replay-with-overrides-code']").text()).toContain("stock_{{ outputs.fetch_store.values.store_code }}")
        expect(wrapper.find("[data-test='replay-confirm']").attributes("disabled")).toBeDefined()

        await wrapper.find("input[aria-label='outputs.fetch_store.values.store_code']").setValue("KR-0412")
        expect(wrapper.find("[data-test='replay-confirm']").text()).toBe("Replay with 1 override")
        await wrapper.find("[data-test='replay-confirm']").trigger("click")

        expect(executionsStore.replayExecutionWithOverrides).toHaveBeenCalledWith({
            executionId: "exec1",
            taskRunId: "tr1",
            overrides: {"outputs.fetch_store.values.store_code": "KR-0412"},
        })
    })

    it("blocks the replay while an override does not match the type of the current output", async () => {
        const {wrapper} = await openDialog()

        await wrapper.find("input[aria-label='outputs.fetch_store.values.qty_delta']").setValue("five")

        expect(wrapper.find("[data-test='replay-override-error']").text()).toBe("Enter a number.")
        expect(wrapper.find("[data-test='replay-confirm']").attributes("disabled")).toBeDefined()
    })
})
