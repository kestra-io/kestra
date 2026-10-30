import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import {ref} from "vue"
import KestraDesignSystem from "@kestra-io/design-system"
import TaskObject from "../../../../../src/components/no-code/components/tasks/TaskObject.vue"
import {DATA_TYPES_MAP_INJECTION_KEY} from "../../../../../src/components/no-code/injectionKeys"

const TRIGGERS = "io.kestra.plugin.core.dashboard.data.Triggers"
const EXECUTIONS = "io.kestra.plugin.core.dashboard.data.Executions"

const schema = {
    properties: {
        type: {const: TRIGGERS},
        columns: {type: "object", additionalProperties: true},
        where: {type: "array", items: {type: "object"}},
    },
}

const modelValue = {
    type: TRIGGERS,
    columns: {nextExec: {field: "NEXT_EXECUTION_DATE"}},
    where: [{field: "NAMESPACE", type: "EQUAL_TO", value: "company"}],
}

function mountData(dataTypes: Record<string, string[]>) {
    const wrapper = mount(TaskObject, {
        props: {modelValue, schema, root: "data", merge: true},
        global: {
            plugins: [
                createI18n({legacy: false, locale: "en", fallbackWarn: false, missingWarn: false}),
                KestraDesignSystem,
            ],
            provide: {[DATA_TYPES_MAP_INJECTION_KEY as symbol]: ref(dataTypes)},
            stubs: {
                Wrapper: {template: "<div><slot name='tasks' /></div>"},
                TaskObjectField: {
                    name: "TaskObjectField",
                    props: ["fieldKey"],
                    emits: ["update:modelValue"],
                    template: "<div class='field-stub' :data-field='fieldKey' />",
                },
            },
        },
    })

    const typeField = wrapper.findAllComponents({name: "TaskObjectField"}).find((field) => field.props("fieldKey") === "type")
    return {wrapper, typeField}
}

describe("TaskObject data type selector", () => {
    test("restarts from the new type when the data type changes", () => {
        const {wrapper, typeField} = mountData({data: [TRIGGERS, EXECUTIONS]})

        typeField?.vm.$emit("update:modelValue", EXECUTIONS)

        expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual([{type: EXECUTIONS}])
    })

    test("keeps the other properties when the type is not a data type selector", () => {
        const {wrapper, typeField} = mountData({})

        typeField?.vm.$emit("update:modelValue", EXECUTIONS)

        expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual([{...modelValue, type: EXECUTIONS}])
    })
})
