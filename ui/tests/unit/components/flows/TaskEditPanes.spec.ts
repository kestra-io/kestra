import {describe, it, expect, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import {createPinia} from "pinia"

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}}),
    useRouter: () => ({replace: () => Promise.resolve(), push: () => Promise.resolve()}),
}))

vi.mock("@kestra-io/design-system", async (importOriginal) => {
    const actual = await importOriginal() as Record<string, unknown>
    return {
        ...actual,
        KsMarkdown: {name: "KsMarkdown", props: ["content"], template: "<div />"},
        KsEditor: {name: "KsEditor", props: ["modelValue", "path", "schemaType", "lang", "readOnly"], template: "<div data-test=\"ks-editor\" />"},
    }
})

vi.mock("../../../../src/components/no-code/components/TaskEditor.vue", () => ({
    default: {name: "TaskEditor", props: ["modelValue", "section"], template: "<div data-test=\"task-editor\" />"},
}))

import TaskEditPanes from "../../../../src/components/flows/TaskEditPanes.vue"

function mountPanes(editorPath?: string) {
    return mount(TaskEditPanes, {
        props: {
            modelValue: "id: some_task\ntype: io.kestra.plugin.core.log.Log\n",
            section: "tasks",
            activeTab: "form",
            editorPath,
        },
        global: {
            plugins: [
                createI18n({legacy: false, locale: "en", messages: {en: {form: "Form", source: "Source"}}}),
                createPinia(),
            ],
            stubs: {
                KsTabs: {name: "KsTabs", template: "<div><slot /></div>"},
                KsTabPane: {name: "KsTabPane", props: ["name"], template: "<div><slot /></div>"},
            },
        },
    })
}

describe("TaskEditPanes", () => {
    it("forwards editorPath to the Source tab's KsEditor as its path prop", () => {
        // Given/When
        const wrapper = mountPanes("verify_backups")

        // Then — regression: without a distinct path, KsEditor derives its Monaco
        // model URI from schemaType alone, so two tasks in the same section (e.g.
        // two open dock tabs) share the same model and silently overwrite each
        // other's content
        const editor = wrapper.findComponent({name: "KsEditor"})
        expect(editor.exists()).toBe(true)
        expect(editor.props("path")).toBe("verify_backups")
    })

    it("gives a different editorPath a different KsEditor path prop", () => {
        // Given/When
        const wrapper = mountPanes("prune_old")

        // Then
        const editor = wrapper.findComponent({name: "KsEditor"})
        expect(editor.props("path")).toBe("prune_old")
    })
})
