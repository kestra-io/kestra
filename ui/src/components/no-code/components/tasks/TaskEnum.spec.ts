import {computed} from "vue"
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
    template: "<div data-testid='namespace-select'></div>",
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

    test("renders when no default namespace is available", () => {
        const wrapper = mountTaskNamespace(undefined, false, "")

        expect(
            wrapper.findComponent({name: "NamespaceSelect"}).exists(),
        ).toBe(true)
    })
})