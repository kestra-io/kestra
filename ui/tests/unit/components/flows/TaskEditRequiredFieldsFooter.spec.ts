import {describe, it, expect, vi} from "vitest"
import {h, inject} from "vue"
import KestraDesignSystem from "@kestra-io/design-system"
import {REQUIRED_FIELDS_TRACKER_INJECTION_KEY} from "../../../../src/components/no-code/injectionKeys"

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}}),
    useRouter: () => ({replace: () => Promise.resolve(), push: () => Promise.resolve()}),
}))

vi.mock("../../../../src/stores/plugins", () => ({
    usePluginsStore: () => ({
        icons: {},
        plugin: undefined,
        editorPlugin: undefined,
        load: vi.fn(() => Promise.resolve()),
    }),
}))

vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({
        user: {isAllowed: () => true},
    }),
}))

vi.mock("../../../../src/stores/flow", () => ({
    useFlowStore: () => ({
        flow: {namespace: "company.team"},
        flowParsed: {},
        taskError: undefined,
        validateTask: vi.fn(() => Promise.resolve({})),
    }),
}))

vi.mock("../../../../src/composables/playground/usePlaygroundRun", () => ({
    usePlaygroundRun: () => ({
        runTask: vi.fn(),
        playgroundStore: {enabled: false},
    }),
}))

// Stands in for the real TaskObjectField tree: it registers a single unset required
// field into the injected tracker, the same way TaskObjectField does deep in the form.
const FakeFormWithOneMissingRequiredField = {
    name: "TaskEditPanes",
    props: ["modelValue", "activeTab", "section", "readOnly", "pluginMarkdown", "editorPath"],
    setup() {
        const tracker = inject(REQUIRED_FIELDS_TRACKER_INJECTION_KEY, undefined)
        tracker?.set("message", "message")
        return () => h("div", {"data-test": "task-edit-panes"}, [
            h("div", {"data-required-path": "message"}, [h("input")]),
        ])
    },
}

vi.mock("../../../../src/components/flows/TaskEditData.vue", () => ({
    default: {name: "TaskEditData", props: ["kind", "title", "subtitle", "sections", "filterable", "collapsible", "isCollapsed", "side"], template: "<div />"},
}))

import TaskEdit from "../../../../src/components/flows/TaskEdit.vue"
import {i18nMount} from "../../i18nMount"

function mountTaskEdit() {
    return i18nMount(TaskEdit, {
        messages: {
            block_editor: {
                required_unset_singular: "1 required field is not set yet",
                required_unset_plural: "{count} required fields are not set yet",
                required_unset_jump: "Jump to first",
            },
        },
        props: {
            task: {id: "verify_backups", type: "io.kestra.plugin.core.log.Log", message: "hi"},
            section: "tasks",
            flowId: "my_flow",
            namespace: "company.team",
            presentation: "panel",
        },
        global: {
            plugins: [KestraDesignSystem],
            stubs: {
                TaskEditPanes: FakeFormWithOneMissingRequiredField,
            },
        },
    })
}

describe("TaskEdit required-fields footer", () => {
    it("shows the singular count and jumps to the missing field on click", async () => {
        const wrapper = mountTaskEdit()
        await wrapper.vm.$nextTick()

        const status = wrapper.find("[data-test='task-edit-required-status']")
        expect(status.exists()).toBe(true)
        expect(status.text()).toContain("1 required field is not set yet")

        const target = wrapper.find("[data-required-path='message']").element as HTMLElement
        target.scrollIntoView = vi.fn()
        const input = target.querySelector("input") as HTMLInputElement
        input.focus = vi.fn()

        await wrapper.find("[data-test='task-edit-required-jump']").trigger("click")

        expect(target.scrollIntoView).toHaveBeenCalledWith({behavior: "smooth", block: "center"})
    })
})
