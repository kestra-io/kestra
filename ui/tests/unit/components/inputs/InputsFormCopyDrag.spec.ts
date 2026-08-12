import {afterEach, beforeEach, describe, expect, test, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import {createI18n} from "vue-i18n"
import KestraDesignSystem, {KsMessage} from "@kestra-io/design-system"
import InputsForm from "../../../../src/components/inputs/InputsForm.vue"

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}, params: {}, name: "flow"}),
    useRouter: () => ({replace: vi.fn(), push: vi.fn()}),
}))

vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({get: vi.fn(), post: vi.fn()}),
}))

vi.mock("override/utils/route", () => ({
    apiUrl: () => "/api/v1",
    apiUrlWithoutTenants: () => "/api/v1",
    baseUrl: "/",
}))

vi.mock("../../../../src/stores/executions", async (importOriginal) => {
    const original = await importOriginal() as any
    return {
        ...original,
        useExecutionsStore: () => ({
            validateExecution: vi.fn().mockResolvedValue({data: {checks: [], inputs: [{
                enabled: true,
                input: {id: "region", type: "STRING", required: false},
                errors: [],
                value: null,
                isDefault: true,
            }]}}),
        }),
    }
})

const copyMock = vi.fn().mockResolvedValue(undefined)

vi.mock("../../../../src/utils/utils", () => ({
    copy: (...args: any[]) => copyMock(...args),
}))

const globalConfig = {
    plugins: [
        createI18n({
            legacy: false,
            locale: "en",
            fallbackWarn: false,
            missingWarn: false,
            messages: {en: {copied: "Copied", copy_to_clipboard: "Copy to clipboard"}},
        }),
        KestraDesignSystem,
    ],
}

const flow = {namespace: "io.kestra.tests", id: "my_flow"} as any
const initialInputs = [{id: "region", type: "STRING", required: false}] as any

function mountForm() {
    return mount(InputsForm, {
        global: globalConfig,
        shallow: true,
        props: {flow, initialInputs},
    })
}

describe("InputsForm copy/drag affordances", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        vi.clearAllMocks()
    })

    afterEach(() => {
        document.body.innerHTML = ""
    })

    test("copyInputRef calls Utils.copy with the correct Pebble reference expression", async () => {
        // Given: the component is mounted with an input "region"
        const wrapper = mountForm()
        await flushPromises()

        const ksMessageSpy = vi.spyOn(KsMessage, "success")

        // When: copyInputRef is called for "region"
        await (wrapper.vm as any).copyInputRef("region")

        // Then: Utils.copy is called with the correct expression
        expect(copyMock).toHaveBeenCalledWith("{{ inputs.region }}")
        expect(ksMessageSpy).toHaveBeenCalled()
    })

    test("onInputDragStart sets effectAllowed and text/plain on the dataTransfer", () => {
        // Given: the component is mounted
        const wrapper = mountForm()

        const dataTransferMock = {
            effectAllowed: "",
            data: {} as Record<string, string>,
            setData(type: string, value: string) { this.data[type] = value },
            getData(type: string) { return this.data[type] },
        }

        // When: a drag is started for input "region"
        const event = {dataTransfer: dataTransferMock} as unknown as DragEvent
        ;(wrapper.vm as any).onInputDragStart(event, "region")

        // Then: effectAllowed is "move" and the reference expression is in text/plain
        expect(dataTransferMock.effectAllowed).toBe("move")
        expect(dataTransferMock.data["text/plain"]).toBe("{{ inputs.region }}")
    })

    test("onInputDragStart is a no-op when dataTransfer is null", () => {
        // Given: the component is mounted
        const wrapper = mountForm()

        // When: a drag event has no dataTransfer (e.g. Firefox programmatic call)
        const event = {dataTransfer: null} as unknown as DragEvent

        // Then: no error is thrown
        expect(() => (wrapper.vm as any).onInputDragStart(event, "region")).not.toThrow()
    })
})
