import {describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import {defineComponent} from "vue"
import * as Utils from "../../utils/utils"
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

// vi.mock is hoisted above plain consts, so the spy has to be hoisted with it.
const {copyToClipboard} = vi.hoisted(() => ({copyToClipboard: vi.fn()}))

vi.mock("@kestra-io/design-system", () => ({
    fileUtils: {isFileUri: () => false},
    copyToClipboard,
    KsEditor: defineComponent({
        name: "KsEditor",
        props: {modelValue: {type: String, default: ""}, options: {type: Object, default: () => ({})}},
        template: "<div data-test=\"ks-editor\" :data-height=\"options.customHeight\">{{ modelValue }}</div>",
    }),
    KsAlert: defineComponent({
        name: "KsAlert",
        props: {title: {type: String, default: ""}},
        template: "<div data-test=\"var-value-truncated\">{{ title }}<slot /></div>",
    }),
}))

const i18n = createI18n({
    legacy: false,
    globalInjection: true,
    locale: "en",
    messages: {
        en: {
            copy: "Copy",
            large_outputs: {
                value_truncated: "Showing a truncated preview of this {size} value.",
            },
        },
    },
})

const KsButtonStub = defineComponent({
    name: "KsButton",
    // No explicit emits: the parent's @click lands as a native listener, so one click fires once.
    // No data-test either: each button's own one falls through, so they stay distinguishable.
    template: "<button><slot /></button>",
})

function mountVarValue(value: unknown, name?: string) {
    return mount(VarValue, {
        props: {value: value as string | object, name},
        global: {plugins: [i18n], stubs: {KsButton: KsButtonStub}},
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

    it("should hand the editor parseable JSON and report the real size when the value is large", () => {
        // The reported shape: one big Ansible-style value arriving as a JSON string.
        const tasks = Array.from({length: 6000}, (_, index) => ({
            uid: `all | arcgis_access_audit : Get the credentials ${index}`,
            name: `Get the credentials ${index}`,
            action: "ansible.builtin.set_fact",
        }))
        const value = JSON.stringify({exitCode: 0, playbooks: {plays: [{name: "all", tasks}]}})

        const wrapper = mountVarValue(value)
        const shown = JSON.parse(editorContent(wrapper))

        expect(shown.playbooks.plays[0].tasks).toHaveLength(101)
        expect(shown.playbooks.plays[0].tasks.at(-1)).toBe("… 5900")
        expect(wrapper.find("[data-test=var-value-truncated]").text()).toContain(
            "Showing a truncated preview of this 1.1 MiB value.",
        )
    })

    it("should cap a long plain string, which never reaches the editor", () => {
        const value = "x".repeat(400 * 1024) + " not json"

        const wrapper = mountVarValue(value)

        expect(wrapper.find("[data-test=ks-editor]").exists()).toBe(false)
        expect(wrapper.text()).not.toContain("not json")
        expect(wrapper.find("[data-test=var-value-truncated]").exists()).toBe(true)
    })

    it("should download the whole value under the output's own name", async () => {
        const value = {tasks: Array.from({length: 6000}, (_, index) => ({uid: `task ${index}`}))}
        const downloadText = vi.spyOn(Utils, "downloadText").mockImplementation(() => {})

        const wrapper = mountVarValue(value, "playbooks.plays")
        await wrapper.find("[data-test=download-full]").trigger("click")

        expect(downloadText).toHaveBeenCalledExactlyOnceWith(
            JSON.stringify(value, null, 2),
            "playbooks.plays.json",
        )
        downloadText.mockRestore()
    })

    it("should copy the whole value, not the truncated one", async () => {
        const value = "x".repeat(400 * 1024) + " not json"
        copyToClipboard.mockClear()

        const wrapper = mountVarValue(value)
        await wrapper.find("[data-test=copy-full]").trigger("click")

        expect(copyToClipboard).toHaveBeenCalledTimes(1)
        expect(copyToClipboard.mock.calls[0][0]).toBe(value)
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
