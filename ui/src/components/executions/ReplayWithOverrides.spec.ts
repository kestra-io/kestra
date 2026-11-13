import {afterAll, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import {i18nMount} from "../../../tests/unit/i18nMount"
import en from "../../translations/en.json"
import {useFlowStore} from "../../stores/flow"
import ReplayWithOverrides from "./ReplayWithOverrides.vue"

const {replayOutputs, replayExecutionWithOverrides} = vi.hoisted(() => ({
    replayOutputs: vi.fn(),
    replayExecutionWithOverrides: vi.fn(),
}))

vi.mock("@kestra-io/kestra-sdk/executions", () => ({replayOutputs, replayExecutionWithOverrides}))

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
  - id: notify
    type: io.kestra.plugin.core.log.Log
    message: "Saved {{ outputs.fetch_store.values.store_code }}"
errors:
  - id: alert
    type: io.kestra.plugin.core.log.Log
    message: "{{ outputs.fetch_store.values }}"
`

const STORE_CODE_READERS = [
    {taskId: "notify", path: "outputs.fetch_store.values.store_code"},
    {taskId: "alert", path: "outputs.fetch_store.values"},
]

async function openDialog(dynamicReaders: string[] = []) {
    Object.defineProperty(window, "location", {value: {href: ""}, writable: true})
    setActivePinia(createPinia())
    const flowStore = useFlowStore()
    flowStore.loadFlow = vi.fn().mockResolvedValue({source: flowSource}) as typeof flowStore.loadFlow
    replayOutputs.mockResolvedValue({values: [
        {path: "outputs.fetch_store.values.store_code", value: "KR-0412 ", readers: STORE_CODE_READERS},
        {path: "outputs.fetch_store.values.qty_delta", value: -12, readers: []},
    ], dynamicReaders})
    replayExecutionWithOverrides.mockResolvedValue({id: "replayed", namespace: "company.stores", flowId: "inventory_batch"})

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
                KsDrawer: {template: "<div><slot name='header' /><slot /></div>"},
                KsEditor: {props: ["modelValue", "path"], template: "<pre :data-path='path'>{{ modelValue }}</pre>"},
            },
        },
    })
    await wrapper.find("[data-test='replay-with-overrides']").trigger("click")
    await flushPromises()
    return {wrapper, flowStore}
}

describe("ReplayWithOverrides", () => {
    it("shows the task as the execution's revision defines it and replays with only the changed outputs", async () => {
        const {wrapper, flowStore} = await openDialog()

        expect(flowStore.loadFlow).toHaveBeenCalledWith(expect.objectContaining({revision: "3"}))
        expect(wrapper.find("[data-test='replay-with-overrides-code']").text()).toContain("stock_{{ outputs.fetch_store.values.store_code }}")
        expect(wrapper.find("[data-test='replay-confirm']").attributes("disabled")).toBeDefined()

        await wrapper.find("input[aria-label='outputs.fetch_store.values.store_code']").setValue("KR-0412")
        expect(wrapper.find("[data-test='replay-confirm']").text()).toBe("Replay with 1 override")
        await wrapper.find("[data-test='replay-confirm']").trigger("click")

        expect(replayExecutionWithOverrides).toHaveBeenCalledWith({
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

    it("counts the other tasks that read an output, shows their definitions and warns once that output is overridden", async () => {
        const {wrapper} = await openDialog()

        const badges = wrapper.findAll("[data-test='replay-override-readers']")
        expect(badges).toHaveLength(1)
        expect(badges[0].text()).toBe("2")
        expect(badges[0].attributes("aria-label")).toBe("Also read by 2 other tasks of the replay")
        expect(wrapper.find("[data-test='replay-overrides-impact']").exists()).toBe(false)

        await wrapper.find("input[aria-label='outputs.fetch_store.values.qty_delta']").setValue("5")
        expect(wrapper.find("[data-test='replay-overrides-impact']").exists()).toBe(false)

        await wrapper.find("input[aria-label='outputs.fetch_store.values.store_code']").setValue("KR-0412")
        expect(wrapper.find("[data-test='replay-overrides-impact']").text()).toBe("These overrides also change what notify, alert read in the replay.")

        await badges[0].trigger("click")
        const readers = wrapper.findAll("[data-test='replay-output-reader']")
        expect(readers.map(reader => reader.find("code").text())).toEqual(["notify", "alert"])
        expect(readers[1].text()).toContain("message: \"{{ outputs.fetch_store.values }}\"")
        const paths = wrapper.findAll("pre").map(editor => editor.attributes("data-path"))
        expect(new Set(paths).size).toBe(paths.length)
    })

    it("warns that tasks reading outputs dynamically may read an override", async () => {
        const {wrapper} = await openDialog(["loop"])
        expect(wrapper.find("[data-test='replay-overrides-impact']").exists()).toBe(false)

        await wrapper.find("input[aria-label='outputs.fetch_store.values.qty_delta']").setValue("5")

        expect(wrapper.find("[data-test='replay-overrides-impact']").text())
            .toBe("loop read outputs through a dynamic path or in a block, and may also read the new values.")
    })
})
