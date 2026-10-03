import {describe, it, expect, vi} from "vitest"
import {createPinia} from "pinia"
import {computed, ref} from "vue"
import {flushPromises} from "@vue/test-utils"
import {
    FULL_SCHEMA_INJECTION_KEY,
    SCHEMA_DEFINITIONS_INJECTION_KEY,
    BLOCK_SCHEMA_PATH_INJECTION_KEY,
} from "../../../../src/components/no-code/injectionKeys"

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

vi.mock("../../../../src/stores/plugins", () => ({
    usePluginsStore: () => ({
        icons: {},
        loadIcon: vi.fn(),
        plugin: undefined,
        editorPlugin: undefined,
        load: vi.fn(() => Promise.resolve({schema: undefined})),
        updateDocumentation: vi.fn(),
    }),
}))

vi.mock("../../../../src/stores/playground", () => ({
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

vi.mock("../../../../src/components/flows/TaskEditData.vue", () => ({
    default: {name: "TaskEditData", props: ["kind", "title", "subtitle", "sections", "filterable", "collapsible", "isCollapsed", "side"], template: "<div />"},
}))

import TaskEdit from "../../../../src/components/flows/TaskEdit.vue"
import {i18nMount} from "../../i18nMount"

// A second item whose nested `connection` object makes TaskArray render it behind a
// collapsed KsDrillRow instead of inline (see `shouldDrillItem`), so its `url` field
// is not in the DOM until the row is drilled into.
const itemSchema = {
    type: "object",
    properties: {
        url: {type: "string"},
        connection: {
            type: "object",
            properties: {timeout: {type: "string"}},
        },
    },
    required: ["url"],
}

const taskSchema = {
    type: "object",
    properties: {
        items: {type: "array", items: itemSchema},
    },
    required: [],
}

function mountTaskEdit() {
    return i18nMount(TaskEdit, {
        // Real DOM attachment, so `document.activeElement` reflects a real .focus() call —
        // an unattached mount leaves every element `isConnected: false` and scrollThenFocus
        // silently bails out before it ever calls .focus().
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
            task: {
                id: "verify_backups",
                items: [
                    {url: "https://example.com/1"},
                    {},
                ],
            },
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

describe("TaskEdit jump into a collapsed drillable array item", () => {
    it("drills into the item and focuses its unset required field", async () => {
        // jsdom has no layout, so the real scrollIntoView would throw before scrollThenFocus
        // ever reaches the rAF-driven focus() call; the target element only exists after the
        // jump has drilled in, so it cannot be stubbed individually ahead of time.
        const scrollIntoView = vi.fn()
        Element.prototype.scrollIntoView = scrollIntoView

        const wrapper = mountTaskEdit()
        await flushPromises()
        await wrapper.vm.$nextTick()
        await flushPromises()
        await wrapper.vm.$nextTick()

        // Given: item #2 is behind a collapsed KsDrillRow, so its `url` field isn't mounted yet
        expect(wrapper.find("[data-test='task-array-item-drill']").exists()).toBe(true)
        expect(wrapper.find("[data-required-path=\"items[1].url\"]").exists()).toBe(false)

        const status = wrapper.find("[data-test='task-edit-required-status']")
        expect(status.exists()).toBe(true)
        expect(status.text()).toContain("1 required field is not set yet")

        // When
        await wrapper.find("[data-test='task-edit-required-jump']").trigger("click")
        await flushPromises()
        await wrapper.vm.$nextTick()

        // Then: the array item is drilled open, and the field it was hiding is focused
        const target = wrapper.find("[data-required-path=\"items[1].url\"]")
        expect(target.exists()).toBe(true)
        expect(scrollIntoView).toHaveBeenCalled()

        await vi.waitFor(() => {
            expect(document.activeElement).toBe(target.element.querySelector("textarea"))
        }, {timeout: 2000, interval: 20})
    })
})
