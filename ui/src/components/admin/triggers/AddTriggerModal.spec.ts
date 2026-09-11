import {describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import {defineComponent} from "vue"
import AddTriggerModal from "./AddTriggerModal.vue"

const SCHEDULE_SCHEMA = {
    properties: {
        required: ["cron", "id", "type"],
        properties: {
            id: {type: "string"},
            type: {type: "string"},
            cron: {type: "string"},
            timezone: {type: "string"},
        },
    },
}

vi.mock("vue-router", () => ({
    useRouter: () => ({push: vi.fn()}),
}))
vi.mock("../../../stores/flow", () => ({
    useFlowStore: () => ({findFlows: vi.fn().mockResolvedValue({results: [{id: "example", namespace: "company.team"}]})}),
}))
vi.mock("../../../stores/plugins", () => ({
    usePluginsStore: () => ({load: vi.fn().mockResolvedValue({schema: SCHEDULE_SCHEMA})}),
}))
vi.mock("../../../stores/triggerDraft", () => ({
    useTriggerDraftStore: () => ({setDraft: vi.fn()}),
}))
vi.mock("../../../composables/useEditorBindings", () => ({
    useEditorBindings: () => ({}),
}))
vi.mock("@kestra-io/design-system", () => ({
    KsEditor: defineComponent({name: "KsEditor", template: "<div />"}),
    copyToClipboard: vi.fn(),
}))
vi.mock("@kestra-io/topology", () => ({
    flowYamlUtils: {stringify: () => ""},
}))
vi.mock("../../namespaces/components/NamespaceSelect.vue", () => ({
    default: defineComponent({name: "NamespaceSelect", props: ["modelValue"], template: "<div />"}),
}))
vi.mock("../../plugins/PluginDocumentation.vue", () => ({
    default: defineComponent({name: "PluginDocumentation", template: "<div />"}),
}))
vi.mock("../../no-code/components/tasks/TaskObject.vue", () => ({
    default: defineComponent({
        name: "TaskObject",
        props: ["modelValue", "properties", "schema"],
        emits: ["update:modelValue"],
        template: "<button data-test=\"fill-cron\" @click=\"$emit('update:modelValue', {cron: '@daily'})\" />",
    }),
}))

const i18n = createI18n({legacy: false, globalInjection: true, locale: "en", messages: {en: {}}})

const slot = {template: "<div><slot /><slot name=\"header\" /><slot name=\"footer\" /></div>"}

function mountModal() {
    return mount(AddTriggerModal, {
        props: {
            visible: true,
            trigger: {type: "io.kestra.plugin.core.trigger.Schedule", name: "Schedule", pluginTitle: "core", description: null, group: "core", ee: false, icon: "", deprecated: null},
            displayName: "Schedule",
        },
        global: {
            plugins: [i18n],
            stubs: {
                KsDialog: slot,
                KsTabs: slot,
                KsTabPane: slot,
                KsForm: slot,
                KsFormItem: slot,
                KsTag: slot,
                KsSkeleton: slot,
                KsIconButton: slot,
                KsSelect: {props: ["modelValue"], template: "<div />"},
                KsOption: {template: "<div />"},
                KsInput: {props: ["modelValue"], template: "<input />"},
                KsButton: {props: ["disabled"], template: "<button :disabled=\"disabled\" v-bind=\"$attrs\"><slot /></button>"},
            },
        },
    })
}

describe("AddTriggerModal", () => {
    it("should enable the submit button once the schema-required fields the form renders are filled", async () => {
        const wrapper = mountModal()
        await flushPromises()

        const vm = wrapper.vm as any
        vm.formModel.namespace = "company.team"
        vm.formModel.flowId = "example"
        vm.formModel.triggerId = "schedule"
        await flushPromises()

        const submit = wrapper.findAll("button").at(-1)!
        expect(submit.attributes("disabled")).toBeDefined()

        await wrapper.find("[data-test=\"fill-cron\"]").trigger("click")

        expect(submit.attributes("disabled")).toBeUndefined()
    })
})
