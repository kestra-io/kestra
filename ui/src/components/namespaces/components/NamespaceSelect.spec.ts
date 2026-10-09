import {describe, test, expect, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import KestraDesignSystem from "@kestra-io/design-system"
import NamespaceSelect from "./NamespaceSelect.vue"
import {i18nMount} from "../../../../tests/unit/i18nMount"

vi.mock("override/stores/namespaces", () => ({
    useNamespacesStore: () => ({autocomplete: [], loadAutocomplete: vi.fn()}),
}))

describe("NamespaceSelect", () => {
    test("mounts with a null model without crashing or filling in a default", async () => {
        setActivePinia(createPinia())
        const errors: unknown[] = []

        const wrapper = i18nMount(NamespaceSelect, {
            props: {modelValue: null as unknown as string[], multiple: true},
            global: {
                plugins: [KestraDesignSystem],
                config: {errorHandler: (error: unknown) => errors.push(error)},
            },
        })
        await flushPromises()

        expect(errors).toEqual([])
        expect(wrapper.emitted("update:modelValue")).toBeUndefined()
    })
})
