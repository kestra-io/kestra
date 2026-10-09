import {describe, test, expect, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import KestraDesignSystem from "@kestra-io/design-system"
import NamespaceSelect from "./NamespaceSelect.vue"
import {i18nMount} from "../../../../tests/unit/i18nMount"

vi.mock("override/stores/namespaces", () => ({
    useNamespacesStore: () => ({autocomplete: [], loadAutocomplete: vi.fn()}),
}))

vi.mock("../../../composables/useNamespaces", () => ({
    defaultNamespace: () => "company.team",
}))

async function mountSelect(modelValue: string | string[] | null | undefined) {
    setActivePinia(createPinia())
    const errors: unknown[] = []
    const wrapper = i18nMount(NamespaceSelect, {
        props: {modelValue, multiple: true},
        global: {
            plugins: [KestraDesignSystem],
            config: {errorHandler: (error: unknown) => errors.push(error)},
        },
    })
    await flushPromises()
    return {wrapper, errors}
}

describe("NamespaceSelect", () => {
    test("mounts with a null model without crashing or filling in a default", async () => {
        const {wrapper, errors} = await mountSelect(null)

        expect(errors).toEqual([])
        expect(wrapper.emitted("update:modelValue")).toBeUndefined()
    })

    test.each([
        ["undefined", undefined, "company.team"],
        ["empty array", [], ["company.team"]],
    ])("fills in the default namespace when the model is %s", async (_name, modelValue, expected) => {
        const {wrapper} = await mountSelect(modelValue)

        expect(wrapper.emitted("update:modelValue")).toEqual([[expected]])
    })
})
