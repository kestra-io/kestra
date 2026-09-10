import {describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import {defineComponent} from "vue"
import VarValue from "./VarValue.vue"

vi.mock("override/utils/route", () => ({
    apiUrl: () => "/api/v1/main",
}))
vi.mock("@kestra-io/kestra-sdk/executions", () => ({
    fileMetadatasFromExecution: vi.fn(),
}))
vi.mock("../../composables/useEditorBindings", () => ({
    useEditorBindings: () => ({}),
}))
vi.mock("./FilePreviewDrawer.vue", () => ({
    default: defineComponent({name: "FilePreviewDrawer", template: "<div />"}),
}))
vi.mock("@kestra-io/design-system", () => ({
    fileUtils: {isFileUri: () => false},
    copyToClipboard: vi.fn(),
    KsEditor: defineComponent({
        name: "KsEditor",
        props: {modelValue: {type: String, default: ""}, options: {type: Object, default: () => ({})}},
        template: "<div data-test=\"ks-editor\" :data-height=\"options.customHeight\">{{ modelValue }}</div>",
    }),
    KsAlert: defineComponent({
        name: "KsAlert",
        props: {title: {type: String, default: ""}},
        template: "<div data-test=\"var-value-truncated\">{{ title }}</div>",
    }),
}))

const i18n = createI18n({
    legacy: false,
    globalInjection: true,
    locale: "en",
    messages: {
        en: {
            large_outputs: {
                value_truncated: "Only the first {lines} lines of this {size} value are shown.",
            },
        },
    },
})

function mountVarValue(value: unknown) {
    return mount(VarValue, {
        props: {value: value as string | object},
        global: {plugins: [i18n]},
    })
}

function editorContent(wrapper: ReturnType<typeof mountVarValue>) {
    return wrapper.find("[data-test=ks-editor]").text()
}

describe("VarValue", () => {
    it("should hand the whole value to the editor when it is small", () => {
        const value = {playbooks: {plays: [{name: "all", tasks: ["a", "b"]}]}}

        const wrapper = mountVarValue(value)

        expect(editorContent(wrapper)).toBe(JSON.stringify(value, null, 2))
        expect(wrapper.find("[data-test=var-value-truncated]").exists()).toBe(false)
    })

    it("should cap the editor content and report the real size when the value is large", () => {
        // The reported shape: one big Ansible-style value arriving as a JSON string.
        const tasks = Array.from({length: 6000}, (_, index) => ({
            uid: `all | arcgis_access_audit : Get the credentials ${index}`,
            name: `Get the credentials ${index}`,
            action: "ansible.builtin.set_fact",
        }))
        const value = JSON.stringify({exitCode: 0, playbooks: {plays: [{name: "all", tasks}]}})

        const wrapper = mountVarValue(value)

        expect(editorContent(wrapper).length).toBeLessThanOrEqual(256 * 1024)
        expect(wrapper.find("[data-test=var-value-truncated]").text()).toBe(
            "Only the first 200 lines of this 1.1 MiB value are shown.",
        )
    })

    it("should stop at the line limit when the value is under the size limit but deep", () => {
        const value = Object.fromEntries(
            Array.from({length: 1000}, (_, index) => [`item_${index}`, index]),
        )

        const wrapper = mountVarValue(value)

        expect(editorContent(wrapper).split("\n")).toHaveLength(200)
        expect(wrapper.find("[data-test=var-value-truncated]").exists()).toBe(true)
    })

    it("should serialize the value once per render", () => {
        const value = {playbooks: {plays: [{name: "all", tasks: ["a", "b"]}]}}
        const stringify = vi.spyOn(JSON, "stringify")

        mountVarValue(value)

        const serializations = stringify.mock.calls.filter((call) => call[1] === null && call[2] === 2)
        expect(serializations).toHaveLength(1)
        stringify.mockRestore()
    })
})
