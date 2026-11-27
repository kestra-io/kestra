import {computed, inject} from "vue"
import {describe, expect, test} from "vitest"
import {i18nMount} from "../../../../../tests/unit/i18nMount"
import TaskNamespace from "./TaskNamespace.vue"
import {
    CREATING_FLOW_INJECTION_KEY,
    DEFAULT_NAMESPACE_INJECTION_KEY,
} from "../../injectionKeys"

const NamespaceSelectStub = {
    name: "NamespaceSelect",
    props: {
        modelValue: String,
        readOnly: Boolean,
    },
    emits: ["update:modelValue"],
    setup() {
        const providedNamespace = inject(DEFAULT_NAMESPACE_INJECTION_KEY, computed(() => ""))
        return {providedNamespace}
    },
    template: "<div data-testid='namespace-select' :data-provided-namespace='providedNamespace'></div>",
}

function mountTaskNamespace(
    modelValue?: string,
    creating = true,
    defaultNamespace = "",
) {
    return i18nMount(TaskNamespace, {
        props: {modelValue},
        global: {
            provide: {
                [CREATING_FLOW_INJECTION_KEY as symbol]: creating,
                [DEFAULT_NAMESPACE_INJECTION_KEY as symbol]: computed(
                    () => defaultNamespace,
                ),
            },
            stubs: {
                NamespaceSelect: NamespaceSelectStub,
            },
        },
    })
}

describe("TaskNamespace", () => {
    test("is editable while the flow is being created", () => {
        const wrapper = mountTaskNamespace("company.team", true)

        expect(
            wrapper.findComponent({name: "NamespaceSelect"}).props("readOnly"),
        ).toBe(false)
    })

    test("emits the selected namespace", async () => {
        const wrapper = mountTaskNamespace("company.team")
        const selector = wrapper.findComponent({name: "NamespaceSelect"})

        await selector.vm.$emit(
            "update:modelValue",
            "company.production",
        )

        expect(wrapper.emitted("update:modelValue")).toEqual([
            ["company.production"],
        ])
    })

    test("uses the provided default namespace when the value is empty", () => {
        const wrapper = mountTaskNamespace(
            "",
            true,
            "company.team",
        )

        expect(wrapper.emitted("update:modelValue")).toEqual([
            ["company.team"],
        ])
    })

    test("keeps an existing namespace instead of applying the default", () => {
        const wrapper = mountTaskNamespace("company.team", true, "company.production")

        expect(wrapper.emitted("update:modelValue")).toBeUndefined()
    })

    test("offers the chosen namespace as the default to the selector's descendants", () => {
        const withValue = mountTaskNamespace("company.team", true, "company.production")
        const withoutValue = mountTaskNamespace(undefined, true, "")

        expect(withValue.find("[data-testid='namespace-select']").attributes("data-provided-namespace")).toBe("company.team")
        expect(withoutValue.find("[data-testid='namespace-select']").attributes("data-provided-namespace")).toBe("")
    })

    test("is read-only and applies no default when the flow is not being created", () => {
        const wrapper = mountTaskNamespace(undefined, false, "")

        expect(
            wrapper.findComponent({name: "NamespaceSelect"}).props("readOnly"),
        ).toBe(true)
        expect(wrapper.emitted("update:modelValue")).toBeUndefined()
    })
})
