import {describe, it, expect, vi} from "vitest"
import KestraDesignSystem from "@kestra-io/design-system"

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}}),
    useRouter: () => ({replace: () => Promise.resolve(), push: () => Promise.resolve()}),
}))

const {pluginsStoreState} = vi.hoisted(() => ({
    pluginsStoreState: {plugin: undefined as {schema?: {outputs?: {properties?: Record<string, unknown>}}} | undefined},
}))

vi.mock("../../../../src/stores/plugins", () => ({
    usePluginsStore: () => ({
        icons: {},
        get plugin() {
            return pluginsStoreState.plugin
        },
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
import {i18nMount} from "../../i18nMount"

function mountTaskEdit() {
    return i18nMount(TaskEdit, {
        messages: {close: "Close"},
        props: {
            task: {id: "verify_backups", type: "io.kestra.plugin.core.log.Log", message: "hi"},
            section: "tasks",
            flowId: "my_flow",
            namespace: "company.team",
            presentation: "panel",
        },
        global: {
            plugins: [KestraDesignSystem],
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

    it("starts the Output column expanded when the task type declares outputs", async () => {
        pluginsStoreState.plugin = {schema: {outputs: {properties: {uri: {type: "string"}}}}}
        const wrapper = mountTaskEdit()
        await wrapper.vm.$nextTick()

        const output = wrapper.findAllComponents({name: "TaskEditData"}).find((c) => c.props("kind") === "output")
        expect(output?.props("isCollapsed")).toBe(false)

        pluginsStoreState.plugin = undefined
    })

    it("does not render an Output column when the task type declares no outputs", async () => {
        pluginsStoreState.plugin = undefined
        const wrapper = mountTaskEdit()
        await wrapper.vm.$nextTick()

        const output = wrapper.findAllComponents({name: "TaskEditData"}).find((c) => c.props("kind") === "output")
        expect(output).toBeUndefined()
    })

    it("re-expands the Output column when switching to another task that also declares outputs", async () => {
        // Regression: switching tasks only reset the "user collapsed it" flag, it never
        // re-derived the collapsed state — so a task the user had collapsed left every
        // later task (declaring outputs or not) stuck in whatever state the first left it.
        pluginsStoreState.plugin = {schema: {outputs: {properties: {uri: {type: "string"}}}}}
        const wrapper = mountTaskEdit()
        await wrapper.vm.$nextTick()

        const output = () => wrapper.findAllComponents({name: "TaskEditData"}).find((c) => c.props("kind") === "output")
        expect(output()?.props("isCollapsed")).toBe(false)

        output()?.vm.$emit("toggle")
        await wrapper.vm.$nextTick()
        expect(output()?.props("isCollapsed")).toBe(true)

        await wrapper.setProps({task: {id: "second_task", type: "io.kestra.plugin.core.log.Log", message: "hi"}})
        await wrapper.vm.$nextTick()

        expect(output()?.props("isCollapsed")).toBe(false)

        pluginsStoreState.plugin = undefined
    })

    it("keeps the armed field when focus moves to a chip control instead of leaving the panel", async () => {
        // Regression: disarming whenever focus left the field for any non-armable target
        // fired on every Tab into a chip button, so a keyboard user could never Tab onto a
        // chip and press Enter/Space to insert — the field was already disarmed by then.
        const wrapper = mountTaskEdit()
        await wrapper.vm.$nextTick()

        const panel = wrapper.get("[data-test='task-edit-panel']").element as HTMLElement
        const field = document.createElement("input")
        const chip = document.createElement("button")
        chip.className = "task-edit-data-chip"
        panel.appendChild(field)
        panel.appendChild(chip)

        field.dispatchEvent(new FocusEvent("focusin", {bubbles: true}))
        expect(field.classList.contains("task-edit-chip-insert-target")).toBe(true)

        field.dispatchEvent(new FocusEvent("focusout", {bubbles: true, relatedTarget: chip}))
        chip.dispatchEvent(new FocusEvent("focusin", {bubbles: true}))

        expect(field.classList.contains("task-edit-chip-insert-target")).toBe(true)
    })
})
