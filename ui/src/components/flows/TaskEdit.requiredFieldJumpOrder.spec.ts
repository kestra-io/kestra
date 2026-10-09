import {describe, it, expect, vi} from "vitest"
import {createPinia} from "pinia"
import {computed, ref} from "vue"
import {flushPromises} from "@vue/test-utils"
import {
    FULL_SCHEMA_INJECTION_KEY,
    SCHEMA_DEFINITIONS_INJECTION_KEY,
    BLOCK_SCHEMA_PATH_INJECTION_KEY,
} from "../no-code/injectionKeys"

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}}),
    useRouter: () => ({replace: () => Promise.resolve(), push: () => Promise.resolve()}),
}))

vi.mock("@kestra-io/design-system", async (importOriginal) => {
    const actual = await importOriginal() as Record<string, unknown>
    return {
        ...actual,
        KsMarkdown: {name: "KsMarkdown", props: ["content"], template: "<div />"},
        // The real KsEditor is Monaco and doesn't run in jsdom; this mirrors its own
        // `monaco-editor-hidden-synced-textarea` accessibility/testing hook so a jump that
        // must focus a string field's editor has a real, focusable element to land on.
        KsEditor: {name: "KsEditor", props: ["modelValue", "path", "schemaType", "lang", "readOnly"], template: "<div data-test=\"ks-editor\"><textarea data-test=\"monaco-editor-hidden-synced-textarea\" /></div>"},
    }
})

vi.mock("../../stores/plugins", () => ({
    usePluginsStore: () => ({
        icons: {},
        loadIcon: vi.fn(),
        plugin: undefined,
        editorPlugin: undefined,
        load: vi.fn(() => Promise.resolve({schema: undefined})),
        updateDocumentation: vi.fn(),
    }),
}))

vi.mock("../../stores/playground", () => ({
    usePlaygroundStore: () => ({enabled: false}),
}))

vi.mock("override/stores/misc", () => ({
    useMiscStore: () => ({configs: {}}),
}))

vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({
        user: {isAllowed: () => true},
    }),
}))

vi.mock("../../stores/flow", () => ({
    useFlowStore: () => ({
        flow: {namespace: "company.team"},
        flowParsed: {},
        taskError: undefined,
        validateTask: vi.fn(() => Promise.resolve({})),
    }),
}))

vi.mock("../../composables/playground/usePlaygroundRun", () => ({
    usePlaygroundRun: () => ({
        runTask: vi.fn(),
        playgroundStore: {enabled: false},
    }),
}))

vi.mock("./TaskEditData.vue", () => ({
    default: {name: "TaskEditData", props: ["kind", "title", "subtitle", "sections", "filterable", "collapsible", "isCollapsed", "side"], template: "<div />"},
}))

import TaskEdit from "./TaskEdit.vue"
import {i18nMount} from "../../../tests/unit/i18nMount"

const taskSchema = {
    type: "object",
    properties: {
        retry: {
            type: "object",
            $group: "reliability",
            properties: {interval: {type: "string", format: "duration"}},
            required: ["interval"],
        },
        token: {type: "string"},
    },
    required: ["token"],
}

function mountTaskEdit(task: Record<string, unknown>) {
    return i18nMount(TaskEdit, {
        attachTo: document.body,
        messages: {
            block_editor: {
                required_unset_singular: "1 required field is not set yet",
                required_unset_plural: "{count} required fields are not set yet",
                required_unset_jump: "Jump to first",
            },
            form: "Form",
            source: "Source",
        },
        props: {
            task: {id: "my_task", ...task},
            section: "tasks",
            flowId: "my_flow",
            namespace: "company.team",
            presentation: "panel",
        },
        global: {
            plugins: [createPinia()],
            provide: {
                [FULL_SCHEMA_INJECTION_KEY as symbol]: ref({definitions: {TestTask: taskSchema}, $ref: ""}),
                [SCHEMA_DEFINITIONS_INJECTION_KEY as symbol]: computed(() => ({})),
                [BLOCK_SCHEMA_PATH_INJECTION_KEY as symbol]: computed(() => "#/definitions/TestTask"),
            },
            stubs: {
                KsTabs: {name: "KsTabs", template: "<div><slot /></div>"},
                KsTabPane: {name: "KsTabPane", props: ["name"], template: "<div><slot /></div>"},
            },
        },
    })
}

async function mountAndSettle(task: Record<string, unknown>) {
    Element.prototype.scrollIntoView = vi.fn()
    const wrapper = mountTaskEdit(task)
    await flushPromises()
    await wrapper.vm.$nextTick()
    await flushPromises()
    return wrapper
}

describe("TaskEdit jump to first unset required field", () => {
    it("follows on-screen order rather than schema order", async () => {
        // Given
        const wrapper = await mountAndSettle({retry: {}})

        // When
        await wrapper.find("[data-test='task-edit-required-jump']").trigger("click")
        await flushPromises()

        // Then
        const tokenField = wrapper.find("[data-required-path='token']")
        await vi.waitFor(() => {
            expect(tokenField.element.contains(document.activeElement)).toBe(true)
        }, {timeout: 2000, interval: 20})
    })

    it("focuses the value input of a duration field, not its inline-code toggle", async () => {
        // Given
        const wrapper = await mountAndSettle({token: "abc", retry: {}})

        // When
        await wrapper.find("[data-test='task-edit-required-jump']").trigger("click")
        await flushPromises()

        // Then
        const field = wrapper.find("[data-required-path='retry.interval']")
        await vi.waitFor(() => {
            expect(document.activeElement?.tagName).not.toBe("BUTTON")
            expect(field.element.contains(document.activeElement)).toBe(true)
        }, {timeout: 2000, interval: 20})
    })
})
