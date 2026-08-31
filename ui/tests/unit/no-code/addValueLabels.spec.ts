import {describe, it, expect} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import Add from "../../../src/components/no-code/components/Add.vue"
import TaskArray from "../../../src/components/no-code/components/tasks/TaskArray.vue"
import TaskDict from "../../../src/components/no-code/components/tasks/TaskDict.vue"
import TaskObjectField from "../../../src/components/no-code/components/tasks/TaskObjectField.vue"

const i18n = createI18n({
    legacy: false,
    locale: "en",
    messages: {
        en: {
            no_code: {
                adding: "+ Add a {what}",
                adding_to: "+ Add to {what}",
                adding_default: "+ Add a new value",
            },
            block_editor: {
                delete: "Delete",
                plugin_default: "default: {value}",
                plugin_default_tooltip: "Plugin default",
                required_missing: "Required field not set",
            },
        },
    },
})

const globalConfig = {
    plugins: [i18n],
    stubs: {
        KsForm: {template: "<form><slot /></form>"},
        KsFormItem: {template: "<div class='stub-form-item'><slot name='label' /><slot /></div>"},
        KsSegmented: {template: "<div class='stub-segmented' />", props: ["modelValue", "options"]},
        KsSelect: {template: "<div class='stub-select'><slot /></div>", props: ["modelValue"]},
        KsOption: {template: "<div />"},
        KsTooltip: {template: "<span><slot /></span>"},
        KsMarkdown: {template: "<span />", props: ["content"]},
        KsIconButton: {inheritAttrs: false, template: "<button v-bind='$attrs'><slot /></button>"},
    },
}

describe("Add", () => {
    it("shows the generic label without a target", () => {
        const wrapper = mount(Add, {global: globalConfig})
        expect(wrapper.text()).toBe("+ Add a new value")
    })

    it("names the target field with the to prop", () => {
        const wrapper = mount(Add, {props: {to: "sla"}, global: globalConfig})
        expect(wrapper.text()).toBe("+ Add to sla")
    })

    it("keeps the legacy what wording when what is given", () => {
        const wrapper = mount(Add, {props: {what: "label"}, global: globalConfig})
        expect(wrapper.text()).toBe("+ Add a label")
    })

    it("emits add on click", async () => {
        const wrapper = mount(Add, {props: {to: "variables"}, global: globalConfig})
        await wrapper.find("button").trigger("click")
        expect(wrapper.emitted("add")).toHaveLength(1)
    })
})

describe("TaskArray add label", () => {
    it("names its field from the root path", () => {
        const wrapper = mount(TaskArray, {
            props: {root: "sla", schema: {type: "array", items: {type: "string"}}},
            global: globalConfig,
        })
        expect(wrapper.find(".add-value-btn").text()).toBe("+ Add to sla")
    })

    it("strips the item index from a nested root", () => {
        const wrapper = mount(TaskArray, {
            props: {root: "tasks[0].headers", schema: {type: "array", items: {type: "string"}}},
            global: globalConfig,
        })
        expect(wrapper.find(".add-value-btn").text()).toBe("+ Add to headers")
    })

    it("falls back to the generic label without a root", () => {
        const wrapper = mount(TaskArray, {
            props: {schema: {type: "array", items: {type: "string"}}},
            global: globalConfig,
        })
        expect(wrapper.find(".add-value-btn").text()).toBe("+ Add a new value")
    })
})

describe("TaskDict add label", () => {
    it("names its field from the root path", () => {
        const wrapper = mount(TaskDict, {
            props: {root: "variables", schema: {type: "object"}},
            global: globalConfig,
        })
        expect(wrapper.find(".add-value-btn").text()).toBe("+ Add to variables")
    })
})

describe("TaskObjectField anyOf containment", () => {
    // The field type resolver loads asynchronously on mount — flush it before
    // asserting which presentation branch rendered.
    async function mountField({schema, fieldKey = "retry"}: {schema: unknown, fieldKey?: string}) {
        const wrapper = mount(TaskObjectField, {
            props: {schema, fieldKey, task: {}, required: []},
            global: globalConfig,
        })
        await flushPromises()
        return wrapper
    }

    it("contains an anyOf of objects in the nested card", async () => {
        const wrapper = await mountField({
            schema: {anyOf: [
                {allOf: [{$ref: "#/definitions/io.kestra.core.models.tasks.retrys.Constant-2"}, {title: "Retry"}]},
                {allOf: [{$ref: "#/definitions/io.kestra.core.models.tasks.retrys.Exponential-2"}, {title: "Retry"}]},
            ]},
        })
        expect(wrapper.find(".nested-card").exists()).toBe(true)
        expect(wrapper.find(".nested-card-label").text()).toBe("retry")
    })

    it("keeps a scalar anyOf on the plain label row", async () => {
        const wrapper = await mountField({
            fieldKey: "message",
            schema: {anyOf: [{type: "string"}, {type: "array", items: {type: "string"}}]},
        })
        expect(wrapper.find(".nested-card").exists()).toBe(false)
        expect(wrapper.find(".inline-wrapper").exists()).toBe(true)
    })

    it("keeps complex object fields in the nested card", async () => {
        const wrapper = await mountField({
            fieldKey: "concurrency",
            schema: {$ref: "#/definitions/io.kestra.core.models.flows.Concurrency"},
        })
        expect(wrapper.find(".nested-card").exists()).toBe(true)
    })
})
