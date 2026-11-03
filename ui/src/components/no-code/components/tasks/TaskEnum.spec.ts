import {computed, inject} from "vue"
import {describe, expect, test} from "vitest"
import {i18nMount} from "../../../../../tests/unit/i18nMount"
import TaskEnum from "./TaskEnum.vue"
import TaskNamespace from "./TaskNamespace.vue"
import {
    CREATING_FLOW_INJECTION_KEY,
    DEFAULT_NAMESPACE_INJECTION_KEY,
} from "../../injectionKeys"

const KsSelectStub = {
    name: "KsSelect",
    props: ["modelValue", "clearable", "placeholder", "allowCreate", "defaultFirstOption", "filterable"],
    emits: ["update:modelValue"],
    template: "<div data-testid='ks-select'><slot /></div>",
}

const KsOptionStub = {
    name: "KsOption",
    props: ["label", "value"],
    template: "<div class='enum-option' :data-value='value'>{{ label }}</div>",
}

const NamespaceSelectStub = {
    name: "NamespaceSelect",
    props: {
        modelValue: String,
        readOnly: Boolean,
        allowCreate: Boolean,
    },
    emits: ["update:modelValue"],
    setup() {
        const providedNamespace = inject(DEFAULT_NAMESPACE_INJECTION_KEY, computed(() => ""))
        return {providedNamespace}
    },
    template: "<div data-testid='namespace-select' :data-provided-namespace='providedNamespace'></div>",
}

type TaskEnumProps = {
    modelValue?: object | string | number | boolean | unknown[]
    schema?: Record<string, unknown>
    required?: boolean
    allowCreate?: boolean
    root?: string
}

function mountTaskEnum(props: TaskEnumProps = {}) {
    return i18nMount(TaskEnum, {
        props,
        messages: {no_code: {choose_placeholder: "Choose {field}"}},
        global: {
            stubs: {
                KsSelect: KsSelectStub,
                KsOption: KsOptionStub,
            },
        },
    })
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

describe("TaskEnum", () => {
    test("renders an option for each schema enum value", () => {
        const wrapper = mountTaskEnum({
            schema: {enum: ["DEBUG", "INFO", "ERROR"]},
        })

        const options = wrapper.findAll(".enum-option")

        expect(options).toHaveLength(3)
        expect(options.map(option => option.text())).toEqual([
            "DEBUG",
            "INFO",
            "ERROR",
        ])
        expect(options.map(option => option.attributes("data-value"))).toEqual([
            "DEBUG",
            "INFO",
            "ERROR",
        ])
    })

    test("does not preselect the schema default when there is no value", () => {
        const wrapper = mountTaskEnum({
            schema: {enum: ["QUEUE", "CANCEL", "FAIL"], default: "QUEUE"},
        })

        expect(
            wrapper.findComponent({name: "KsSelect"}).props("modelValue"),
        ).toBeUndefined()
    })

    test("passes the current model value to the selector", () => {
        const wrapper = mountTaskEnum({
            modelValue: "INFO",
            schema: {enum: ["DEBUG", "INFO", "ERROR"]},
        })

        expect(
            wrapper.findComponent({name: "KsSelect"}).props("modelValue"),
        ).toBe("INFO")
    })

    test("emits the selected value", async () => {
        const wrapper = mountTaskEnum({
            schema: {enum: ["DEBUG", "INFO", "ERROR"]},
        })

        await wrapper
            .findComponent({name: "KsSelect"})
            .vm.$emit("update:modelValue", "ERROR")

        expect(wrapper.emitted("update:modelValue")).toEqual([["ERROR"]])
    })

    test.each(["", null, {}])("emits undefined when the selection is cleared to %j", async (cleared) => {
        const wrapper = mountTaskEnum({
            schema: {enum: ["DEBUG", "INFO", "ERROR"]},
        })

        await wrapper
            .findComponent({name: "KsSelect"})
            .vm.$emit("update:modelValue", cleared)

        expect(wrapper.emitted("update:modelValue")).toEqual([[undefined]])
    })

    test("is clearable unless the field is required", () => {
        const optional = mountTaskEnum({schema: {enum: ["DEBUG"]}})
        const required = mountTaskEnum({schema: {enum: ["DEBUG"]}, required: true})

        expect(optional.findComponent({name: "KsSelect"}).props("clearable")).toBe(true)
        expect(required.findComponent({name: "KsSelect"}).props("clearable")).toBe(false)
    })

    test("lets the user create a value only when allowCreate is set", () => {
        const fixed = mountTaskEnum({schema: {enum: ["DEBUG"]}})
        const creatable = mountTaskEnum({schema: {enum: ["DEBUG"]}, allowCreate: true})

        expect(fixed.findComponent({name: "KsSelect"}).props()).toMatchObject({allowCreate: false, defaultFirstOption: false})
        expect(creatable.findComponent({name: "KsSelect"}).props()).toMatchObject({allowCreate: true, defaultFirstOption: true})
    })

    test("names the field from the last segment of its root in the placeholder", () => {
        const nested = mountTaskEnum({schema: {enum: ["QUEUE"]}, root: "concurrency.behavior"})
        const unnamed = mountTaskEnum({schema: {enum: ["QUEUE"]}})

        expect(nested.findComponent({name: "KsSelect"}).props("placeholder")).toBe("Choose behavior")
        expect(unnamed.findComponent({name: "KsSelect"}).props("placeholder")).toBe("Choose value")
    })

    test("renders no options when the schema has no enum", () => {
        const wrapper = mountTaskEnum({
            schema: {type: "string"},
        })

        expect(wrapper.findAll(".enum-option")).toHaveLength(0)
    })
})

describe("TaskNamespace", () => {
    test("renders a namespace selector configured for flows", () => {
        const wrapper = mountTaskNamespace("company.team", true)
        const selector = wrapper.findComponent({name: "NamespaceSelect"})

        expect(selector.exists()).toBe(true)
        expect(selector.attributes("data-type")).toBe("flow")
        expect(selector.props("allowCreate")).toBe(true)
        expect(selector.props("readOnly")).toBe(false)
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