import {describe, it, expect, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "@kestra-io/design-system"

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

vi.mock("../../../../src/components/flows/TaskEditPanes.vue", () => ({
    default: {name: "TaskEditPanes", props: ["modelValue", "activeTab", "section", "readOnly", "pluginMarkdown", "editorPath"], template: "<div data-test='task-edit-panes' />"},
}))

vi.mock("../../../../src/components/flows/TaskEditData.vue", () => ({
    default: {name: "TaskEditData", props: ["kind", "title", "subtitle", "sections", "filterable", "collapsible", "isCollapsed", "side"], template: "<div />"},
}))

import TaskEdit from "../../../../src/components/flows/TaskEdit.vue"

function mountTaskEdit() {
    return mount(TaskEdit, {
        props: {
            task: {id: "verify_backups", type: "io.kestra.plugin.core.log.Log", message: "hi"},
            section: "tasks",
            flowId: "my_flow",
            namespace: "company.team",
            presentation: "panel",
        },
        global: {
            plugins: [
                createI18n({legacy: false, locale: "en", messages: {en: {close: "Close"}}}),
                KestraDesignSystem,
            ],
        },
    })
}

describe("TaskEdit", () => {
    it("emits close when the per-pane tabstrip's close button is clicked", async () => {
        // Given — regression: this button used to only flip a local isModalOpen flag,
        // so clicking it in a tiled split-view pane left a stale entry in the parent's
        // dock tabs (the outer tabbar still showed it) instead of actually closing it
        const wrapper = mountTaskEdit()
        await wrapper.vm.$nextTick()

        // When
        await wrapper.find("[data-test='task-edit-tab-close']").trigger("click")

        // Then
        expect(wrapper.emitted("close")).toBeTruthy()
    })

    it("flushPendingEdit emits an edit immediately instead of waiting out the debounce", async () => {
        // Regression: typing then immediately pressing Cmd/Ctrl+S raced the
        // 500ms input debounce, silently saving the flow without the last
        // edit. The parent now calls the exposed flushPendingEdit() before
        // saving so a pending edit is never dropped.
        vi.useFakeTimers()
        const wrapper = mountTaskEdit()
        await wrapper.vm.$nextTick()

        const panes = wrapper.findComponent({name: "TaskEditPanes"})
        panes.vm.$emit("input", "id: verify_backups\ntype: io.kestra.plugin.core.log.Log\nmessage: edited")

        // Then — nothing emitted yet, the debounce hasn't fired
        expect(wrapper.emitted("update:task")).toBeFalsy()

        // When
        ;(wrapper.vm as unknown as {flushPendingEdit: () => void}).flushPendingEdit()

        // Then — the edit is committed right away, not after the 500ms timer
        const emitted = wrapper.emitted("update:task")
        expect(emitted).toBeTruthy()
        expect(emitted![0][0]).toContain("message: edited")

        vi.useRealTimers()
    })
})
