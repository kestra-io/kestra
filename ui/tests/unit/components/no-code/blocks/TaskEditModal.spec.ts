import {describe, it, expect, vi, afterEach} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import TaskEditModal from "../../../../../src/components/no-code/blocks/TaskEditModal.vue"
import en from "../../../../../src/translations/en.json"

vi.mock("../../../../../src/stores/plugins", () => ({usePluginsStore: () => ({load: vi.fn()})}))
vi.mock("../../../../../src/components/no-code/blocks/TaskEditModalForm.vue", () => ({default: {template: "<div />"}}))
vi.mock("../../../../../src/components/no-code/blocks/BlockCreateForm.vue", () => ({default: {template: "<div />"}}))
vi.mock("../../../../../src/components/no-code/components/FieldNavBreadcrumb.vue", () => ({default: {template: "<div />"}}))
vi.mock("../../../../../src/components/plugins/PluginDocumentation.vue", () => ({default: {template: "<div />"}}))

const i18n = createI18n({legacy: false, locale: "en", messages: en})

const stubsWithoutDialog = {
    KsIconButton: {template: "<button><slot /></button>"},
    KsText: {template: "<span><slot /></span>"},
    RouterLink: {props: ["to"], template: "<a><slot /></a>"},
}

const stubs = {
    ...stubsWithoutDialog,
    KsDialog: {template: "<div><slot name=\"header\" /><slot /><slot name=\"footer\" /></div>"},
}

describe("TaskEditModal", () => {
    it("renders the open-mode hint as one sentence, with the settings link inside it", () => {
        const wrapper = mount(TaskEditModal, {
            props: {
                section: "tasks" as const,
                flowId: "my-flow",
                namespace: "company.team",
                editorKey: "tasks.0",
                parentPath: "tasks",
                blockSchemaPath: "tasks",
                crumbs: [],
                task: {id: "log", type: "io.kestra.plugin.core.log.Log"},
            },
            global: {plugins: [i18n], stubs},
        })

        const hint = wrapper.get("[data-test=\"task-edit-modal-open-mode-hint\"]")
        expect(hint.find("span").text()).toBe("Tasks open in a modal by default. You can make tabs the default in Settings.")
        expect(hint.find("a").text()).toBe("Settings")
    })

    // The modal is appendToBody, so the keyboard guard claims it by marker rather than by DOM
    // containment. That delivery runs through KsDialog's $attrs and element-plus's own forwarding,
    // neither of which this repo's code owns — so it is asserted against the real dialog.
    describe("authoring-overlay marker", () => {
        afterEach(() => {
            document.body.className = ""
            document.body.querySelectorAll(".el-overlay, .el-dialog").forEach(node => node.remove())
        })

        it("reaches a field inside the rendered dialog", async () => {
            mount(TaskEditModal, {
                props: {
                    section: "tasks" as const,
                    flowId: "my-flow",
                    namespace: "company.team",
                    editorKey: "tasks.0",
                    parentPath: "tasks",
                    blockSchemaPath: "tasks",
                    crumbs: [],
                    task: {id: "log", type: "io.kestra.plugin.core.log.Log"},
                },
                global: {plugins: [i18n], stubs: stubsWithoutDialog},
                attachTo: document.body,
            })
            await new Promise(resolve => setTimeout(resolve, 0))

            const marked = document.querySelector("[data-authoring-overlay]")
            expect(marked, "the dialog carries the marker").not.toBeNull()
            // Pins that this ran against the real dialog rather than a stub standing in for it.
            expect(marked!.classList.contains("el-dialog")).toBe(true)

            const field = document.createElement("input")
            marked!.appendChild(field)
            expect(field.closest("[data-authoring-overlay]")).toBe(marked)
        })
    })
})
