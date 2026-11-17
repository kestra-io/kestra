import {afterAll, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import {i18nMount} from "../../../tests/unit/i18nMount"
import en from "../../translations/en.json"
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
    {id: "tr-alert", taskId: "alert", state: state("SUCCESS", "2026-01-01T10:00:08Z", "2026-01-01T10:00:09Z")},
]

const select = {
    props: ["modelValue"],
    emits: ["change"],
    template: "<select data-test='replay-override-add' @change=\"$emit('change', $event.target.value)\"><option value='' /><slot /></select>",
}

async function openDialog() {
    Object.defineProperty(window, "location", {value: {href: ""}, writable: true})
    setActivePinia(createPinia())
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
                KsSelect: select,
                KsOption: {props: ["value", "label"], template: "<option :value='value'>{{ label }}</option>"},
                KsEditor: {
                    props: ["modelValue"],
                    emits: ["update:modelValue"],
                    template: "<textarea :value='modelValue' @input=\"$emit('update:modelValue', $event.target.value)\" />",
                },
            },
        },
    })
    await wrapper.find("[data-test='replay-with-overrides']").trigger("click")
    await flushPromises()
    return wrapper
}

describe("ReplayWithOverrides", () => {
    it("offers only the task runs that ended before the replayed one started", async () => {
        const wrapper = await openDialog()

        expect(wrapper.findAll("[data-test='replay-override-add'] option").map(option => option.text()).filter(Boolean)).toEqual(["fetch_store"])
    })

    it("replays with the edited outputs of the picked task run, and not before they change", async () => {
        const wrapper = await openDialog()

        await wrapper.find("[data-test='replay-override-add']").setValue("tr-fetch")
        await flushPromises()
        expect(taskRunOutputs).toHaveBeenCalledWith({executionId: "exec1", taskRunId: "tr-fetch"})
        expect(wrapper.find("[data-test='replay-confirm']").attributes("disabled")).toBeDefined()

        await wrapper.find("[data-test='replay-override-tr-fetch'] textarea").setValue("{\"values\": {\"store_code\": \"KR-0412\", \"qty_delta\": -12}}")
        await wrapper.find("[data-test='replay-confirm']").trigger("click")

        expect(replayExecutionWithOverrides).toHaveBeenCalledWith({
            executionId: "exec1",
            taskRunId: "tr-save",
            outputs: {"tr-fetch": {values: {store_code: "KR-0412", qty_delta: -12}}},
        })
    })

    it("blocks the replay while the edited outputs are not a JSON object", async () => {
        const wrapper = await openDialog()
        await wrapper.find("[data-test='replay-override-add']").setValue("tr-fetch")
        await flushPromises()

        await wrapper.find("[data-test='replay-override-tr-fetch'] textarea").setValue("[1, 2")

        expect(wrapper.find("[data-test='replay-override-error']").exists()).toBe(true)
        expect(wrapper.find("[data-test='replay-confirm']").attributes("disabled")).toBeDefined()
    })
})
