import {afterEach, beforeEach, describe, expect, test, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {i18nMount} from "../../i18nMount"

import {createPinia, setActivePinia} from "pinia"
import KestraDesignSystem from "@kestra-io/design-system"
import InputsForm from "../../../../src/components/inputs/InputsForm.vue"
import {useExecutionsStore, type InputError, type InputMetaData, type ValidationResponse} from "../../../../src/stores/executions"
import type {Flow} from "../../../../src/stores/flow"

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}, params: {}, name: "flow"}),
    useRouter: () => ({replace: vi.fn(), push: vi.fn()}),
}))

const flow = {namespace: "company.team", id: "provision_vm"} as unknown as Flow

const DISKS: InputMetaData = {
    id: "disks",
    type: "TABLE",
    required: false,
    rows: {min: 1, max: 2},
    columns: [
        {id: "size_gb", type: "INT", min: 10, max: 2048},
        {id: "mountpoint", type: "STRING"},
    ],
}

/** Echoes the submitted rows back, with whatever per-cell errors the test declares. */
function stubValidate(errors: InputError[] = []) {
    return vi.fn(({formData}: {formData?: FormData}): Promise<ValidationResponse> => Promise.resolve({
        checks: [],
        inputs: [{
            enabled: true,
            isDefault: false,
            value: JSON.parse((formData?.get("disks") as string) ?? "[]"),
            errors,
            input: DISKS,
        }],
    }))
}

function mountForm() {
    return i18nMount(InputsForm, {
        global: {plugins: [KestraDesignSystem]},
        props: {flow, initialInputs: [DISKS]},
    })
}

describe("InputsForm TABLE", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
    })

    afterEach(() => {
        document.body.innerHTML = ""
    })

    test("submits the rows as a JSON array of objects keyed by column", async () => {
        const store = useExecutionsStore()
        store.validateExecution = stubValidate()

        const wrapper = mountForm()
        await flushPromises()

        // `rows.min: 1` seeds the first row; Add appends the second and stops there at `rows.max: 2`.
        await wrapper.find("[data-test=\"table-row-add-disks\"]").trigger("click")
        await flushPromises()
        await wrapper.find("[data-test=\"table-cell-disks-1-mountpoint\"]").setValue("/dev/sdb")
        await flushPromises()

        expect(JSON.parse(wrapper.vm.inputsValues.disks)).toEqual([
            {size_gb: undefined, mountpoint: undefined},
            {size_gb: undefined, mountpoint: "/dev/sdb"},
        ])
        expect(wrapper.find("[data-test=\"table-row-add-disks\"]").attributes("disabled")).toBeDefined()
    })

    test("flags the offending cell in place rather than under the whole input", async () => {
        const store = useExecutionsStore()
        store.validateExecution = stubValidate([{
            message: "Invalid value for input `disks[0].size_gb`. Cause: it must be less than `2048`",
            path: "disks[0].size_gb",
        }])

        const wrapper = mountForm()
        await flushPromises()

        expect(wrapper.find("[data-test=\"table-cell-disks-0-size_gb\"]").exists()).toBe(true)
        expect(wrapper.text()).toContain("it must be less than `2048`")
        // The verbose backend message belongs to the cell, so the form item must not repeat it.
        expect(wrapper.text()).not.toContain("Invalid value for input")
    })

    test("keeps the last row when `rows.min` is reached", async () => {
        const store = useExecutionsStore()
        store.validateExecution = stubValidate()

        const wrapper = mountForm()
        await flushPromises()

        expect(wrapper.find("[data-test=\"table-row-remove-disks-0\"]").attributes("disabled")).toBeDefined()
    })
})
