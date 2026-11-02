import {describe, it, expect, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import KestraDesignSystem from "@kestra-io/design-system"

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}}),
    useRouter: () => ({replace: () => Promise.resolve(), push: () => Promise.resolve()}),
}))

vi.mock("../../stores/plugins", () => ({
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

const sourceOf = (message: string) => `id: my_flow\nnamespace: company.team\ntasks:\n  - id: hello\n    type: io.kestra.plugin.core.log.Log\n    message: ${message}\n`

// Revision 3 is the deletion, which the revisions list leaves out.
const REVISIONS = [1, 2, 4, 5].map(revision => ({revision, source: sourceOf(`revision ${revision}`)}))

vi.mock("../../stores/flow", () => ({
    useFlowStore: () => ({
        flow: undefined,
        flowParsed: {},
        loadFlow: vi.fn(() => Promise.resolve()),
        loadRevisions: vi.fn(() => Promise.resolve(REVISIONS)),
        validateTask: vi.fn(() => Promise.resolve({})),
    }),
}))

vi.mock("../../composables/playground/usePlaygroundRun", () => ({
    usePlaygroundRun: () => ({
        runTask: vi.fn(),
        playgroundStore: {enabled: false},
    }),
}))

vi.mock("../../composables/useContextSections", async () => {
    const {ref} = await import("vue")
    return {useContextSections: () => ({sections: ref([])})}
})

vi.mock("./TaskEditPanes.vue", () => ({
    default: {
        name: "TaskEditPanes",
        props: ["modelValue", "activeTab", "section", "readOnly", "pluginMarkdown", "editorPath"],
        template: "<pre data-test='task-edit-panes'>{{ modelValue }}</pre>",
    },
}))

import TaskEdit from "./TaskEdit.vue"
import {i18nMount} from "../../../tests/unit/i18nMount"

async function openTaskSource(revision: number) {
    const wrapper = i18nMount(TaskEdit, {
        props: {
            component: "button",
            readOnly: true,
            taskId: "hello",
            section: "tasks",
            flowId: "my_flow",
            namespace: "company.team",
            revision,
        },
        global: {
            plugins: [KestraDesignSystem],
            stubs: {
                KsDrawer: {template: "<div><slot /><slot name='footer' /></div>"},
            },
        },
    })
    await wrapper.find("button").trigger("click")
    await flushPromises()
    return wrapper
}

describe("TaskEdit revision", () => {
    it("shows the source of the execution's revision, without the old revision warning, on the latest revision of a recreated flow", async () => {
        const wrapper = await openTaskSource(5)

        expect(wrapper.get("[data-test='task-edit-panes']").text()).toContain("message: revision 5")
        expect(wrapper.find("[data-test='task-edit-revision-alert']").exists()).toBe(false)
    })

    it("shows the source of the execution's revision, with the old revision warning, on an older revision of a recreated flow", async () => {
        const wrapper = await openTaskSource(4)

        expect(wrapper.get("[data-test='task-edit-panes']").text()).toContain("message: revision 4")
        expect(wrapper.find("[data-test='task-edit-revision-alert']").exists()).toBe(true)
    })
})
