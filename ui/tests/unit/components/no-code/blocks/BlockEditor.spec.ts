import {describe, it, expect, vi, beforeEach, afterEach} from "vitest"
import {ref} from "vue"
import {mount, flushPromises} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import {createPinia} from "pinia"

// --- YAML fixtures ---

const SIMPLE_YAML = `
id: my_flow
namespace: company.team
tasks:
  - id: log_task
    type: io.kestra.plugin.core.log.Log
    message: Hello
  - id: http_task
    type: io.kestra.plugin.core.http.Request
    uri: https://example.com
`.trim()

const YAML_WITH_FLOWABLE = `
id: my_flow
namespace: company.team
tasks:
  - id: leaf
    type: io.kestra.plugin.core.log.Log
    message: ok
  - id: if_block
    type: io.kestra.plugin.core.flow.If
    condition: "{{ true }}"
    then:
      - id: nested_a
        type: io.kestra.plugin.core.log.Log
        message: in then
    else:
      - id: nested_b
        type: io.kestra.plugin.core.log.Log
        message: in else
`.trim()

const YAML_WITH_SWITCH = `
id: my_flow
namespace: company.team
tasks:
  - id: sw
    type: io.kestra.plugin.core.flow.Switch
    value: "{{ inputs.env }}"
    cases:
      prod:
        - id: prod_log
          type: io.kestra.plugin.core.log.Log
          message: Production
      dev:
        - id: dev_log
          type: io.kestra.plugin.core.log.Log
          message: Development
`.trim()

const YAML_WITH_TRIGGERS = `
id: my_flow
namespace: company.team
tasks:
  - id: log
    type: io.kestra.plugin.core.log.Log
    message: ok
triggers:
  - id: webhook
    type: io.kestra.plugin.core.trigger.Webhook
    key: abc
`.trim()

const EMPTY_YAML = "id: my_flow\nnamespace: company.team"

const YAML_WITH_DUPLICATE_IDS = `
id: my_flow
namespace: company.team
tasks:
  - id: dup_task
    type: io.kestra.plugin.core.log.Log
    message: first
  - id: dup_task
    type: io.kestra.plugin.core.log.Log
    message: second
`.trim()

// --- Shared reactive state for store mock ---

const mockFlowYaml = ref(SIMPLE_YAML)
const mockOnEdit = vi.fn()

// --- Store and UI mocks ---
// vi.mock factories are hoisted before imports. Keep factories self-contained.

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}}),
    useRouter: () => ({replace: () => Promise.resolve(), push: () => Promise.resolve()}),
}))

vi.mock("../../../../../src/stores/flow", () => ({
    useFlowStore: () => ({
        get flowYaml() { return mockFlowYaml.value },
        set flowYaml(v: string) { mockFlowYaml.value = v },
        flow: {id: "my_flow", namespace: "company.team"},
        onEdit: mockOnEdit,
    }),
}))

const mockPlugins = [
    {
        name: "core",
        title: "Core",
        group: "io.kestra.plugin.core",
        tasks: [
            {cls: "io.kestra.plugin.core.log.Log", title: "Log"},
            {cls: "io.kestra.plugin.core.flow.If", title: "If"},
        ],
    },
]
const mockEnsurePlugins = vi.fn().mockResolvedValue(mockPlugins)

vi.mock("../../../../../src/stores/plugins", () => ({
    usePluginsStore: () => ({
        icons: {},
        plugins: mockPlugins,
        ensurePlugins: mockEnsurePlugins,
    }),
}))

const confirmMock = vi.fn().mockResolvedValue(undefined)

vi.mock("@kestra-io/design-system", async (importOriginal) => {
    const actual = await importOriginal<typeof import("@kestra-io/design-system")>()
    return {
        ...actual,
        KsTaskIcon: {template: "<span data-test='task-icon' />"},
        KsMessageBox: {confirm: (...args: unknown[]) => confirmMock(...args)},
    }
})

// inheritAttrs: false prevents BlockEditor's data-test="block-card" attr from
// overwriting the stub's own data-test="flowable-cluster-card" attr.
vi.mock("../../../../../src/components/no-code/blocks/FlowableClusterCard.vue", () => ({
    default: {
        name: "FlowableClusterCard",
        inheritAttrs: false,
        props: {
            block: {type: Object, required: true},
            path: {type: String, required: true},
            icons: Object,
            selectedId: String,
            focusedId: String,
            depth: Number,
        },
        emits: ["select", "delete", "duplicate", "add-at-path"],
        template: `
            <div data-test="flowable-cluster-card" :data-block-id="block.id">
                <div data-test="flowable-cluster-header" aria-expanded="true" :class="{'block-kbd-focused': focusedId === block.id}" />
                <span data-test="block-card-id">{{ block.id }}</span>
                <span data-test="block-card-type">{{ String(block.type).split('.').pop() }}</span>
                <div v-if="block.then" data-test="branch-lane-then">
                    <span v-for="t in block.then" :key="t.id" :data-block-id="t.id" :class="{'block-kbd-focused': focusedId === t.id}" data-test="nested-block-card">{{ t.id }}</span>
                </div>
                <div v-if="block.else" data-test="branch-lane-else">
                    <span v-for="t in block.else" :key="t.id" :data-block-id="t.id" :class="{'block-kbd-focused': focusedId === t.id}" data-test="nested-block-card">{{ t.id }}</span>
                </div>
                <div v-if="block.cases && block.cases.prod" data-test="branch-lane-cases.prod">
                    <span v-for="t in block.cases.prod" :key="t.id">{{ t.id }}</span>
                </div>
                <div v-if="block.cases && block.cases.dev" data-test="branch-lane-cases.dev">
                    <span v-for="t in block.cases.dev" :key="t.id">{{ t.id }}</span>
                </div>
                <button data-test="block-card-delete" @click="$emit('delete', path)">Delete</button>
                <button data-test="block-card-duplicate" @click="$emit('duplicate', path)">Dup</button>
                <button data-test="cluster-add-btn" @click="$emit('add-at-path', path + '.then', -1)">Add</button>
            </div>
        `,
    },
}))

vi.mock("../../../../../src/components/no-code/blocks/BranchLane.vue", () => ({
    default: {
        name: "BranchLane",
        inheritAttrs: false,
        props: ["laneName", "tasks", "parentPath", "icons", "selectedId", "depth"],
        template: "<div data-test='branch-lane'><slot /></div>",
    },
}))

// TaskEdit stub: expose({open}) references taskEditOpenSpy, but vi.mock factories
// are hoisted before const declarations. Work around by NOT using expose/spy —
// instead verify rendering and state changes from the parent vm.
// The stub carries a minimal version of TaskEdit's real "panel" layout (three
// columns: inputs / params(form) / output, each with a focusable field) so
// tests can exercise BlockEditor's dock-pane keyboard navigation (which
// queries .task-edit-col-inputs/-params/-output by real DOM structure)
// against something more realistic than a bare div.
vi.mock("../../../../../src/components/flows/TaskEdit.vue", () => ({
    default: {
        name: "TaskEdit",
        inheritAttrs: false,
        props: ["task", "section", "flowId", "namespace", "isHidden"],
        emits: ["update:task", "close"],
        setup(_: unknown, {expose}: {expose: (o: Record<string, unknown>) => void}) {
            expose({open: () => undefined})
        },
        template: `
            <div data-test='block-editor-task-edit' v-bind="$attrs">
                <div class="task-edit-col-inputs">
                    <input data-test="stub-inputs-field" />
                </div>
                <div class="task-edit-col-params">
                    <button data-test="stub-doc-toggle">Documentation</button>
                    <div role="tablist">
                        <div role="tab" tabindex="0" data-test="stub-tab-header">Form</div>
                    </div>
                    <input data-test="stub-form-field-1" />
                    <input data-test="stub-form-field-2" />
                </div>
                <div class="task-edit-col-output">
                    <button data-test="stub-output-chip">chip</button>
                </div>
            </div>
        `,
    },
}))

// --- i18n messages ---

const messages = {
    en: {
        block_editor: {
            add_switch_case: "Add case",
            add_task: "Add task",
            add_to_lane: "Add to {lane}",
            card_aria_label: "Block {id} of type {type}",
            cluster_collapse_aria: "Collapse {id}",
            cluster_expand_aria: "Expand {id} ({count} tasks)",
            collapsed_summary: "{count} tasks",
            confirm_delete: {
                message: "This removes {name} from the flow.",
                message_group: "This removes the group {name} and everything inside it.",
                title: "Delete {name}?",
            },
            delete: "Delete",
            depth_pill: "Depth {depth}",
            duplicate: "Duplicate",
            empty: "No tasks or triggers yet.",
            flowable_todo: "Nested tasks (editing coming soon)",
            lane_case: "Case: {key}",
            lane_defaults: "Default",
            lane_else: "Else",
            lane_empty: "No tasks in {lane}",
            lane_errors: "Errors",
            lane_finally: "Finally",
            lane_tasks: "Tasks",
            lane_then: "Then",
            nested_count: "{count} nested tasks",
            loading_plugins: "Loading plugins...",
            drag_reorder: "Drag to reorder",
            move_down: "Move down",
            move_up: "Move up",
            nav_rail_empty: "No tasks or triggers yet.",
            nav_rail_title: "Outline",
            no_task_results: "No matching task types.",
            pick_task_type: "Choose a task type",
            search_task_placeholder: "Search task types...",
            switch_case_key_placeholder: "New case key",
            then_required_warning: "Then branch is required",
            all_apps: "All apps",
            app_actions: "{count} actions",
            block_deleted: "{name} deleted",
            inserting_into: "Inserting into {section}",
            kbd_add: "add",
            kbd_close: "close",
            kbd_navigate: "navigate",
            no_recent: "No recent task types yet",
            picker_more_results: "{count} more, refine your search",
            tab_apps: "By app",
            tab_recent: "Recent",
            tab_suggested: "Suggested",
            undo: "Undo",
            command_menu: {
                context_flow: "context: flow",
                context_selected: "context: {name}",
                delete: "Delete {name}",
                duplicate: "Duplicate {name}",
                goto: "Go to {section}",
                group_block: "This block",
                group_flow: "Flow",
                group_goto: "Go to",
                group_insert: "Insert",
                insert_after: "Insert task after {name}",
                insert_at_end: "Insert task at end of Tasks",
                insert_before: "Insert task before {name}",
                insert_in_section: "Insert task in {section}",
                insert_kind: "Insert {kind}",
                no_match: "No match",
                open: "Open {name}",
                save: "Save flow",
                search_placeholder: "Type a command or search a task…",
                title: "Command menu",
            },
            footer: {
                canvas: "Canvas",
                close_panel: "Close panel",
                command_menu: "Command menu",
                editing: "Editing block",
                inserting: "Inserting",
                selected: "Selected · {name}",
            },
            shortcuts: {
                add_after: "Insert a block after selection",
                add_before: "Insert a block before selection",
                add_task: "Quick insert at cursor",
                clear: "Close / clear selection",
                collapse_expand: "Collapse / expand",
                command_palette: "Command menu (contextual)",
                group_edit: "Edit",
                group_global: "Global",
                group_insert: "Insert",
                group_navigate: "Navigate",
                move_between: "Move selection",
                open: "Open selected block",
                open_split: "Open selected block in split view",
                or: "or",
                reorder: "Reorder up / down",
                save: "Save flow",
                step_into: "Expand / step into a group",
                step_out: "Collapse / step out to parent",
                title: "Keyboard shortcuts",
                toggle: "This help",
                undo: "Undo",
            },
        },
        no_code: {
            sections: {tasks: "Tasks", triggers: "Triggers"},
        },
        add: "Add",
        cancel: "Cancel",
        copy: "Copy",
        delete: "Delete",
    },
}

// --- Import component under test (after all vi.mock calls) ---

import BlockEditor from "../../../../../src/components/no-code/blocks/BlockEditor.vue"
import {storageKeys, taskEditDefaultModes} from "../../../../../src/utils/constants"

// --- Global mount config ---

const makeConfig = () => ({
    global: {
        plugins: [
            createI18n({legacy: false, locale: "en", messages}),
            createPinia(),
        ],
        stubs: {
            KsEmpty: {template: "<div data-test='ks-empty'><slot /></div>"},
            KsDialog: {
                template: "<div v-if='modelValue' data-test='ks-dialog'><slot /></div>",
                props: ["modelValue", "title"],
                emits: ["update:modelValue"],
            },
            KsInput: {methods: {focus() {}}, template: "<input data-test='ks-input' />"},
            KsIconButton: {
                inheritAttrs: false,
                template: "<button v-bind='$attrs'><slot /></button>",
            },
            KsLoading: {template: "<div data-test='ks-loading' />"},
        },
    },
})

// --- Tests ---

describe("BlockEditor", () => {
    // Shared across tests so afterEach can always unmount the last mounted
    // instance — required because the keyboard composable listens on window,
    // and a leaked instance would keep reacting to later tests' key events.
    let wrapper: ReturnType<typeof mount> | undefined

    beforeEach(() => {
        mockFlowYaml.value = SIMPLE_YAML
        mockOnEdit.mockClear()
    })

    afterEach(() => {
        wrapper?.unmount()
        wrapper = undefined
        localStorage.clear()
    })

    describe("rendering blocks from YAML", () => {
        it("renders task block cards from the flow YAML", () => {
            // Given

            // When
            wrapper = mount(BlockEditor, makeConfig())

            // Then
            const cards = wrapper.findAll("[data-test='block-card']")
            expect(cards.length).toBeGreaterThanOrEqual(2)
        })

        it("shows task ids in block cards", () => {
            // Given

            // When
            wrapper = mount(BlockEditor, makeConfig())

            // Then
            const ids = wrapper.findAll("[data-test='block-card-id']").map(el => el.text())
            expect(ids).toContain("log_task")
            expect(ids).toContain("http_task")
        })

        it("shows short task type in block cards", () => {
            // Given

            // When
            wrapper = mount(BlockEditor, makeConfig())

            // Then
            const types = wrapper.findAll("[data-test='block-card-type']").map(el => el.text())
            expect(types).toContain("Log")
            expect(types).toContain("Request")
        })

        it("renders trigger cards when triggers exist", () => {
            // Given
            mockFlowYaml.value = YAML_WITH_TRIGGERS

            // When
            wrapper = mount(BlockEditor, makeConfig())

            // Then
            const triggerList = wrapper.find("[data-test='block-editor-trigger-list']")
            expect(triggerList.exists()).toBe(true)
            const ids = triggerList.findAll("[data-test='block-card-id']").map(el => el.text())
            expect(ids).toContain("webhook")
        })

        it("renders all six section cards even when the flow is empty", () => {
            // Given
            mockFlowYaml.value = EMPTY_YAML

            // When
            wrapper = mount(BlockEditor, makeConfig())

            // Then
            // flow, triggers, tasks, errors, finally, afterExecution
            expect(wrapper.findAll("[data-test^='block-section-']").length).toBe(6)
            expect(wrapper.findAll("[data-test='block-card']").length).toBe(0)
            expect(wrapper.find("[data-test='block-editor-add-task']").exists()).toBe(true)
        })

        it("renders a FlowableClusterCard for flowable tasks instead of a plain BlockCard", () => {
            // Given
            mockFlowYaml.value = YAML_WITH_FLOWABLE

            // When
            wrapper = mount(BlockEditor, makeConfig())

            // Then
            const clusters = wrapper.findAll("[data-test='flowable-cluster-card']")
            expect(clusters.length).toBeGreaterThan(0)
            const clusterIds = clusters.map(c => c.find("[data-test='block-card-id']").text())
            expect(clusterIds).toContain("if_block")
        })

        it("still renders a plain BlockCard for leaf tasks alongside a flowable", () => {
            // Given
            mockFlowYaml.value = YAML_WITH_FLOWABLE

            // When
            wrapper = mount(BlockEditor, makeConfig())

            // Then
            const leafCards = wrapper.findAll("[data-test='block-card']")
            const leafIds = leafCards.map(c => c.find("[data-test='block-card-id']")?.text())
            expect(leafIds).toContain("leaf")
        })

        it("renders both then and else lanes inside the flowable cluster for an If task", () => {
            // Given
            mockFlowYaml.value = YAML_WITH_FLOWABLE

            // When
            wrapper = mount(BlockEditor, makeConfig())

            // Then — stub renders lane divs keyed by lane name
            const cluster = wrapper.find("[data-test='flowable-cluster-card']")
            expect(cluster.exists()).toBe(true)
            expect(cluster.find("[data-test='branch-lane-then']").exists()).toBe(true)
            expect(cluster.find("[data-test='branch-lane-else']").exists()).toBe(true)
        })

        it("renders case lanes for Switch tasks", () => {
            // Given
            mockFlowYaml.value = YAML_WITH_SWITCH

            // When
            wrapper = mount(BlockEditor, makeConfig())

            // Then
            const cluster = wrapper.find("[data-test='flowable-cluster-card']")
            expect(cluster.exists()).toBe(true)
            expect(cluster.find("[data-test='branch-lane-cases.prod']").exists()).toBe(true)
            expect(cluster.find("[data-test='branch-lane-cases.dev']").exists()).toBe(true)
        })
    })

    describe("duplicate task ids", () => {
        it("gives sibling tasks that share an id distinct data-block-id values", () => {
            // Given
            mockFlowYaml.value = YAML_WITH_DUPLICATE_IDS

            // When
            wrapper = mount(BlockEditor, makeConfig())

            // Then
            const cards = wrapper.findAll("[data-test='block-card']")
            expect(cards).toHaveLength(2)
            const domIds = cards.map(c => c.attributes("data-block-id"))
            expect(new Set(domIds).size).toBe(2)
        })

        it("only rings the specific card that is focused, not every card sharing its id", async () => {
            // Given — regression: the ring used to compare by raw task.id, so focusing
            // one of two same-id siblings lit up both at once
            mockFlowYaml.value = YAML_WITH_DUPLICATE_IDS
            wrapper = mount(BlockEditor, makeConfig())
            const cards = wrapper.findAll("[data-test='block-card']")
            const secondDomId = cards[1].attributes("data-block-id")

            // When
            const vm = wrapper.vm as unknown as {focusedId?: string}
            vm.focusedId = secondDomId
            await wrapper.vm.$nextTick()

            // Then
            const refreshed = wrapper.findAll("[data-test='block-card']")
            expect(refreshed[0].classes()).not.toContain("block-kbd-focused")
            expect(refreshed[1].classes()).toContain("block-kbd-focused")
        })
    })

    describe("block selection", () => {
        it("selects a block when clicked", async () => {
            // Given
            wrapper = mount(BlockEditor, makeConfig())
            const firstCard = wrapper.find("[data-test='block-card']")

            // When
            await firstCard.trigger("click")

            // Then
            expect(firstCard.classes()).toContain("block-card--selected")
        })

        it("keeps a block selected when its card is clicked again", async () => {
            // Given
            wrapper = mount(BlockEditor, makeConfig())
            const firstCard = wrapper.find("[data-test='block-card']")
            await firstCard.trigger("click")
            await wrapper.vm.$nextTick()

            // When — clicking the same card again does not toggle it off
            await wrapper.find("[data-test='block-card']").trigger("click")
            await wrapper.vm.$nextTick()

            // Then — the block stays open and selected (closing happens via the
            // shared dock, outside this component)
            expect(wrapper.find("[data-test='block-card']").classes()).toContain("block-card--selected")
        })

        it("emits editTask with the resolved parentPath/blockSchemaPath/refPath instead of hosting its own dock", async () => {
            // Given — the dock-handoff (TAB) mode: a card click emits editTask to
            // the shared dock instead of opening the local modal (the default).
            localStorage.setItem(storageKeys.TASK_EDIT_DEFAULT_MODE, taskEditDefaultModes.TAB)
            wrapper = mount(BlockEditor, makeConfig())
            const firstCard = wrapper.find("[data-test='block-card']")

            // When
            await firstCard.trigger("click")
            await wrapper.vm.$nextTick()

            // Then — the merge plan: BlockEditor hands off to the flow editor's
            // shared MultiPanelTabs dock (via useNoCodePanels.ts) instead of
            // mounting TaskEdit itself
            expect(wrapper.findComponent({name: "TaskEdit"}).exists()).toBe(false)
            const emitted = wrapper.emitted("editTask")
            expect(emitted).toBeTruthy()
            expect(emitted![0]).toEqual(["tasks", expect.stringContaining("properties/tasks/items"), 0, false])
        })

        it("emits editTask with split=true when the card's open-in-split button is clicked", async () => {
            // Given
            wrapper = mount(BlockEditor, makeConfig())

            // When
            await wrapper.find("[data-test='block-card-open-split']").trigger("click")
            await wrapper.vm.$nextTick()

            // Then — the split flag routes the task into a new dock panel beside
            // the canvas rather than a same-place tab
            const emitted = wrapper.emitted("editTask")
            expect(emitted).toBeTruthy()
            expect(emitted![0]).toEqual(["tasks", expect.stringContaining("properties/tasks/items"), 0, true])
        })

        it("deselecting via an external delete emits closeTask", async () => {
            // Given
            wrapper = mount(BlockEditor, makeConfig())
            await wrapper.find("[data-test='block-card']").trigger("click")
            await wrapper.vm.$nextTick()

            // When — deleting the selected block clears the selection and tells
            // the owner (the shared dock) its tab is no longer valid
            await wrapper.find("[data-test='block-card-delete']").trigger("click")
            await wrapper.vm.$nextTick()

            // Then
            expect(wrapper.emitted("closeTask")).toBeTruthy()
        })

        it("emits editTask again (with the new block's index) when another block is clicked", async () => {
            // Given — dock-handoff (TAB) mode so each card click emits editTask.
            localStorage.setItem(storageKeys.TASK_EDIT_DEFAULT_MODE, taskEditDefaultModes.TAB)
            wrapper = mount(BlockEditor, makeConfig())
            const cards = wrapper.findAll("[data-test='block-card']")

            // When — open two different blocks in sequence
            await cards[0].trigger("click")
            await wrapper.vm.$nextTick()
            await cards[1].trigger("click")
            await wrapper.vm.$nextTick()

            // Then — each click hands off its own editTask to the shared dock,
            // which owns the actual tab/split/tile behavior
            const emitted = wrapper.emitted("editTask")
            expect(emitted).toHaveLength(2)
            expect(emitted![0]).toEqual(["tasks", expect.any(String), 0, false])
            expect(emitted![1]).toEqual(["tasks", expect.any(String), 1, false])
        })
    })

    describe("inline edit mode (rendered by the shared dock)", () => {
        it("renders only TaskEdit for the resolved task when editingTask is true", () => {
            // Given / When — mirrors how useNoCodePanels.ts mounts BlockEditor
            // as the dock tab's component once nocodeEngine=blocks
            wrapper = mount(BlockEditor, {
                ...makeConfig(),
                props: {editingTask: true, parentPath: "tasks", refPath: 0},
            })

            // Then
            expect(wrapper.find("[data-test='block-editor']").exists()).toBe(false)
            const taskEdit = wrapper.findComponent({name: "TaskEdit"})
            expect(taskEdit.exists()).toBe(true)
            expect(taskEdit.props("section")).toBe("tasks")
            expect((taskEdit.props("task") as Record<string, unknown>).id).toBe("log_task")
        })

        it("writes the updated YAML back to the store when TaskEdit emits update:task", async () => {
            // Given
            wrapper = mount(BlockEditor, {
                ...makeConfig(),
                props: {editingTask: true, parentPath: "tasks", refPath: 0},
            })

            // When
            const updatedTaskYaml = "id: log_task\ntype: io.kestra.plugin.core.log.Log\nmessage: Updated message"
            const taskEditEl = wrapper.findComponent({name: "TaskEdit"})
            await taskEditEl.vm.$emit("update:task", updatedTaskYaml)
            await wrapper.vm.$nextTick()

            // Then
            const {flowYamlUtils} = await import("@kestra-io/topology")
            const parsed = flowYamlUtils.parse(mockFlowYaml.value)
            expect(parsed.tasks).toHaveLength(2)
            expect(parsed.tasks[0].message).toBe("Updated message")
            expect(parsed.tasks[1].id).toBe("http_task")
        })

        it("emits closeTask when TaskEdit emits close", async () => {
            // Given
            wrapper = mount(BlockEditor, {
                ...makeConfig(),
                props: {editingTask: true, parentPath: "tasks", refPath: 0},
            })

            // When — a save emits update:task then closes the panel (as TaskEdit.saveTask does)
            const taskEditEl = wrapper.findComponent({name: "TaskEdit"})
            await taskEditEl.vm.$emit("close")
            await wrapper.vm.$nextTick()

            // Then
            expect(wrapper.emitted("closeTask")).toBeTruthy()
        })
    })

    describe("delete operation", () => {
        it("removes a leaf task from the store when delete is clicked", async () => {
            // Given
            wrapper = mount(BlockEditor, makeConfig())
            const deleteBtns = wrapper.findAll("[data-test='block-card-delete']")

            // When
            await deleteBtns[0].trigger("click")
            await wrapper.vm.$nextTick()

            // Then
            const {flowYamlUtils} = await import("@kestra-io/topology")
            const parsed = flowYamlUtils.parse(mockFlowYaml.value)
            expect(parsed.tasks).toHaveLength(1)
        })

        it("deletes a nested block when FlowableClusterCard emits delete with a path", async () => {
            // Given
            mockFlowYaml.value = YAML_WITH_FLOWABLE
            wrapper = mount(BlockEditor, makeConfig())
            const cluster = wrapper.findComponent({name: "FlowableClusterCard"})

            // When
            await cluster.vm.$emit("delete", "tasks[1].then[0]")
            await wrapper.vm.$nextTick()

            // Then
            const {flowYamlUtils} = await import("@kestra-io/topology")
            const parsed = flowYamlUtils.parse(mockFlowYaml.value)
            const thenLen = Array.isArray(parsed.tasks[1].then) ? parsed.tasks[1].then.length : 0
            expect(thenLen).toBe(0)
            expect(parsed.tasks[1].else[0].id).toBe("nested_b")
        })
    })

    describe("duplicate operation", () => {
        it("adds a copy of a leaf task when duplicate is clicked", async () => {
            // Given
            wrapper = mount(BlockEditor, makeConfig())
            const duplicateBtns = wrapper.findAll("[data-test='block-card-duplicate']")

            // When
            await duplicateBtns[0].trigger("click")
            await wrapper.vm.$nextTick()

            // Then
            const {flowYamlUtils} = await import("@kestra-io/topology")
            const parsed = flowYamlUtils.parse(mockFlowYaml.value)
            expect(parsed.tasks).toHaveLength(3)
            expect(parsed.tasks.some((t: Record<string, unknown>) =>
                String(t.id).startsWith("log_task_copy"),
            )).toBe(true)
        })

        it("duplicates a nested block when FlowableClusterCard emits duplicate with a path", async () => {
            // Given
            mockFlowYaml.value = YAML_WITH_FLOWABLE
            wrapper = mount(BlockEditor, makeConfig())
            const cluster = wrapper.findComponent({name: "FlowableClusterCard"})

            // When
            await cluster.vm.$emit("duplicate", "tasks[1].then[0]")
            await wrapper.vm.$nextTick()

            // Then
            const {flowYamlUtils} = await import("@kestra-io/topology")
            const parsed = flowYamlUtils.parse(mockFlowYaml.value)
            expect(parsed.tasks[1].then).toHaveLength(2)
            expect(String(parsed.tasks[1].then[1].id)).toMatch(/^nested_a_copy/)
        })
    })

    describe("add task", () => {
        it("shows the add task button", () => {
            // Given

            // When
            wrapper = mount(BlockEditor, makeConfig())

            // Then
            expect(wrapper.find("[data-test='block-editor-add-task']").exists()).toBe(true)
        })

        it("inserts a nested task when FlowableClusterCard requests add-at-path then user calls insertTask", async () => {
            // Given
            mockFlowYaml.value = YAML_WITH_FLOWABLE
            wrapper = mount(BlockEditor, makeConfig())
            const cluster = wrapper.findComponent({name: "FlowableClusterCard"})

            // When — cluster signals insert into then lane
            await cluster.vm.$emit("add-at-path", "tasks[1].then", -1)
            await wrapper.vm.$nextTick()

            // Then — insertTask uses the primed parentPath
            const vm = wrapper.vm as unknown as {picker: {insertTask: (fqcn: string) => void}}
            vm.picker.insertTask("io.kestra.plugin.core.log.Log")
            await wrapper.vm.$nextTick()

            const {flowYamlUtils} = await import("@kestra-io/topology")
            const parsed = flowYamlUtils.parse(mockFlowYaml.value)
            expect(parsed.tasks[1].then).toHaveLength(2)
        })

        function pickerNames() {
            const picker = document.body.querySelector("[data-test='block-editor-picker']")
            return [...(picker?.querySelectorAll(".block-editor-picker-name") ?? [])].map(n => n.textContent)
        }

        it("populates the picker list from pluginsStore plugin data", async () => {
            vi.useFakeTimers()
            try {
                // Given
                wrapper = mount(BlockEditor, makeConfig())
                await wrapper.find("[data-test='block-editor-add-task']").trigger("click")
                const vm = wrapper.vm as unknown as {picker: {taskPickerSearch: {value: string}}}

                // When — a search that matches every mock entry
                vm.picker.taskPickerSearch.value = "io.kestra"
                await wrapper.vm.$nextTick()
                vi.advanceTimersByTime(200)
                await wrapper.vm.$nextTick()

                // Then — picker entries come from mockPlugins
                expect(pickerNames()).toEqual(["Log", "If"])
            } finally {
                vi.useRealTimers()
            }
        })

        it("filters picker entries by search text (after debounce)", async () => {
            vi.useFakeTimers()
            try {
                // Given
                wrapper = mount(BlockEditor, makeConfig())
                await wrapper.find("[data-test='block-editor-add-task']").trigger("click")
                const vm = wrapper.vm as unknown as {picker: {taskPickerSearch: {value: string}}}

                // When — the search input is debounced before filtering
                vm.picker.taskPickerSearch.value = "If"
                await wrapper.vm.$nextTick()
                expect(pickerNames()).toEqual(["Log", "If"])
                vi.advanceTimersByTime(200)
                await wrapper.vm.$nextTick()

                // Then
                expect(pickerNames()).toEqual(["If"])
            } finally {
                vi.useRealTimers()
            }
        })
    })

    describe("drag-to-reorder", () => {
        it("reorders tasks when a drag sequence completes on the top-level task list", async () => {
            // Given
            wrapper = mount(BlockEditor, makeConfig())
            const cards = wrapper.findAll("[data-test='block-card']")
            expect(cards).toHaveLength(2)

            // When — simulate drag from index 0 to index 1
            await cards[0].trigger("dragstart", {dataTransfer: {effectAllowed: ""}})
            await cards[1].trigger("dragover")
            await cards[1].trigger("drop")
            await wrapper.vm.$nextTick()

            // Then — the order is reversed in the store YAML
            const {flowYamlUtils} = await import("@kestra-io/topology")
            const parsed = flowYamlUtils.parse(mockFlowYaml.value)
            expect(parsed.tasks[0].id).toBe("http_task")
            expect(parsed.tasks[1].id).toBe("log_task")
        })

        it("clears selection when a nested-child edit is open and the parent task is drag-reordered", async () => {
            // Given — flow with leaf at tasks[0], flowable at tasks[1]; nested child open via path tasks[1].then[0]
            mockFlowYaml.value = YAML_WITH_FLOWABLE
            wrapper = mount(BlockEditor, makeConfig())
            const cluster = wrapper.findComponent({name: "FlowableClusterCard"})

            // Select a nested child inside tasks[1]
            await cluster.vm.$emit("select", "tasks[1].then[0]")
            await wrapper.vm.$nextTick()
            await wrapper.vm.$nextTick()

            const vm = wrapper.vm as unknown as {
                activeSelectedPath: string | undefined
                activeSelectedId: string | undefined
                handleTaskDragStart: (event: DragEvent, index: number) => void
                handleTaskDrop: (event: DragEvent, index: number) => void
            }
            expect(vm.activeSelectedPath).toBe("tasks[1].then[0]")
            expect(vm.activeSelectedId).toBe("nested_a")

            // When — prime drag from tasks[0], then drop on tasks[1]
            // This shifts the flowable from [1] to [0], making tasks[1].then[0] stale
            const mockEvent = {preventDefault: () => undefined, dataTransfer: {effectAllowed: ""}} as unknown as DragEvent
            vm.handleTaskDragStart(mockEvent, 0)
            vm.handleTaskDrop(mockEvent, 1)
            await wrapper.vm.$nextTick()

            // Then — stale path is detected and selection cleared
            expect(vm.activeSelectedId).toBeUndefined()
            expect(vm.activeSelectedPath).toBeUndefined()
        })

        it("keeps a nested selection when an unrelated sibling lane is reordered", async () => {
            // Given — a nested child open at tasks[1].then[0]
            mockFlowYaml.value = YAML_WITH_FLOWABLE
            wrapper = mount(BlockEditor, makeConfig())
            const cluster = wrapper.findComponent({name: "FlowableClusterCard"})
            cluster.vm.$emit("select", "tasks[1].then[0]")
            await wrapper.vm.$nextTick()
            await wrapper.vm.$nextTick()

            const vm = wrapper.vm as unknown as {
                activeSelectedPath: string | undefined
                activeSelectedId: string | undefined
            }
            expect(vm.activeSelectedPath).toBe("tasks[1].then[0]")
            expect(vm.activeSelectedId).toBe("nested_a")

            // When — a DIFFERENT lane of the same flowable (else) is reordered.
            // The old index-only check matched the outer tasks[1] and wrongly
            // cleared; keying on the reordered parentPath must leave it alone.
            cluster.vm.$emit("reorder", "tasks[1].else", 0, 1)
            await wrapper.vm.$nextTick()

            // Then — the then-lane selection is untouched
            expect(vm.activeSelectedPath).toBe("tasks[1].then[0]")
            expect(vm.activeSelectedId).toBe("nested_a")
        })

        it("emits update:selectedId when selectedId changes via v-model", async () => {
            // Given
            wrapper = mount(BlockEditor, {
                ...makeConfig(),
                props: {selectedId: undefined},
            })

            // When — click a card to select
            await wrapper.find("[data-test='block-card']").trigger("click")
            await wrapper.vm.$nextTick()

            // Then — emitted update:selectedId with the block id
            const emitted = wrapper.emitted("update:selectedId")
            expect(emitted).toBeTruthy()
            expect(emitted![0][0]).toBe("log_task")
        })
    })

    describe("keyboard shortcuts", () => {
        function windowKeydown(options: KeyboardEventInit) {
            window.dispatchEvent(new KeyboardEvent("keydown", {bubbles: true, cancelable: true, ...options}))
        }

        function mountBlockEditor() {
            wrapper = mount(BlockEditor, makeConfig())
            return wrapper
        }

        beforeEach(() => {
            confirmMock.mockClear()
            confirmMock.mockResolvedValue(undefined)
        })

        it("Delete key removes the selected leaf task after confirmation", async () => {
            // Given
            const wrapper = mountBlockEditor()
            await wrapper.find("[data-test='block-card']").trigger("click")
            await wrapper.vm.$nextTick()

            // When
            windowKeydown({key: "Delete"})
            await flushPromises()
            await wrapper.vm.$nextTick()

            // Then
            expect(confirmMock).toHaveBeenCalledTimes(1)
            const {flowYamlUtils} = await import("@kestra-io/topology")
            const parsed = flowYamlUtils.parse(mockFlowYaml.value)
            expect(parsed.tasks).toHaveLength(1)
        })

        it("Backspace key removes the selected leaf task after confirmation", async () => {
            // Given
            const wrapper = mountBlockEditor()
            await wrapper.find("[data-test='block-card']").trigger("click")
            await wrapper.vm.$nextTick()

            // When
            windowKeydown({key: "Backspace"})
            await flushPromises()
            await wrapper.vm.$nextTick()

            // Then
            const {flowYamlUtils} = await import("@kestra-io/topology")
            const parsed = flowYamlUtils.parse(mockFlowYaml.value)
            expect(parsed.tasks).toHaveLength(1)
        })

        it("does not delete when the confirmation is cancelled", async () => {
            // Given
            confirmMock.mockRejectedValue(new Error("cancel"))
            const wrapper = mountBlockEditor()
            await wrapper.find("[data-test='block-card']").trigger("click")
            await wrapper.vm.$nextTick()

            // When
            windowKeydown({key: "Delete"})
            await flushPromises()
            await wrapper.vm.$nextTick()

            // Then
            const {flowYamlUtils} = await import("@kestra-io/topology")
            const parsed = flowYamlUtils.parse(mockFlowYaml.value)
            expect(parsed.tasks).toHaveLength(2)
        })

        it("ArrowRight steps focus from a flowable group into its first nested task", async () => {
            // Given — YAML_WITH_FLOWABLE: leaf (index 0), if_block (index 1) with then[nested_a].
            // navigableCards() filters on offsetParent !== null to skip hidden cards, but
            // jsdom never computes real layout so offsetParent is always null — stub it so
            // the filter behaves like a real browser for this test.
            const offsetParentSpy = vi.spyOn(HTMLElement.prototype, "offsetParent", "get").mockReturnValue(document.body)
            mockFlowYaml.value = YAML_WITH_FLOWABLE
            const wrapper = mountBlockEditor()
            await flushPromises()
            await wrapper.vm.$nextTick()
            // Triggers has no items in this fixture, so its empty-section sentinel is the
            // first navigable stop, ahead of the real task cards.
            windowKeydown({key: "ArrowDown"}) // focus the empty Triggers section
            await wrapper.vm.$nextTick()
            windowKeydown({key: "ArrowDown"}) // focus leaf
            await wrapper.vm.$nextTick()
            windowKeydown({key: "ArrowDown"}) // focus if_block
            await wrapper.vm.$nextTick()

            // When
            windowKeydown({key: "ArrowRight"})
            await flushPromises()
            await wrapper.vm.$nextTick()

            // Then — focus landed on the nested task inside the group, not a sibling at
            // the top level (regression: nested cards used to have no data-block-id at
            // all, so the DOM lookup silently found nothing and step-into no-opped)
            const nested = wrapper.find("[data-block-id='nested_a']")
            expect(nested.exists()).toBe(true)
            expect(nested.classes()).toContain("block-kbd-focused")
            offsetParentSpy.mockRestore()
        })

        it("inserts into a flowable block's own empty errors lane when its sentinel is focused", async () => {
            // Given — if_block (per FLOWABLE_SUFFIX_MAP) also has its own "errors"/"finally"
            // lanes alongside then/else; BranchLane renders those as an always-present
            // sentinel (__lane:<path>) when empty (regression: that lane had no sentinel at
            // all, so it was unreachable via keyboard and had no way to insert a first
            // error/finally task). FlowableClusterCard/BranchLane are hand-rolled stubs in
            // this file that don't model that sentinel, so focusedId is set directly here to
            // exercise BlockEditor's own lane-sentinel handling in isolation.
            mockFlowYaml.value = YAML_WITH_FLOWABLE
            const wrapper = mountBlockEditor()
            await flushPromises()
            const vm = wrapper.vm as unknown as {
                focusedId?: string
                taskPickerVisible: boolean
                picker: {insertTask: (fqcn: string) => void}
                commandMenuItems: Array<{id: string; title: string}>
            }

            // When
            vm.focusedId = "__lane:tasks[1].errors"
            await wrapper.vm.$nextTick()

            // Then — a lane sentinel is not a real block, so Open/Duplicate/Delete/insert-before
            // stay hidden — same contract as a top-level empty section
            const itemIds = vm.commandMenuItems.map(i => i.id)
            expect(itemIds).toContain("insert")
            expect(itemIds).not.toContain("insert-before")
            expect(itemIds).not.toContain("open")
            expect(itemIds).not.toContain("duplicate")
            expect(itemIds).not.toContain("delete")

            // When — "a" opens the picker anchored on that lane, not the top-level Errors section
            windowKeydown({key: "a"})
            await wrapper.vm.$nextTick()

            // Then — inserting from it lands in that lane, not the flow-level Errors section
            expect(vm.taskPickerVisible).toBe(true)
            vm.picker.insertTask("io.kestra.plugin.core.log.Log")
            await wrapper.vm.$nextTick()
            const {flowYamlUtils} = await import("@kestra-io/topology")
            const parsed = flowYamlUtils.parse(mockFlowYaml.value) as {tasks: {errors?: unknown[]}[]; errors?: unknown[]}
            expect(parsed.tasks[1].errors).toHaveLength(1)
            expect(parsed.errors).toBeUndefined()
        })

        it("ignores Delete on an empty-section sentinel instead of opening a confirm dialog", async () => {
            // Given — regression: the confirm dialog used to open with the internal
            // sentinel id leaked as the block name ("Delete __section:errors?")
            const wrapper = mountBlockEditor()
            const vm = wrapper.vm as unknown as {focusedId?: string}
            vm.focusedId = "__section:errors"
            await wrapper.vm.$nextTick()

            // When
            windowKeydown({key: "Backspace"})
            await flushPromises()

            // Then
            expect(confirmMock).not.toHaveBeenCalled()

            // And the same for a lane sentinel
            vm.focusedId = "__lane:tasks[0].errors"
            await wrapper.vm.$nextTick()
            windowKeydown({key: "Delete"})
            await flushPromises()
            expect(confirmMock).not.toHaveBeenCalled()
        })

        it("moves focus to the next block after deleting the focused one", async () => {
            // Given — regression: focus used to be cleared entirely, so the next
            // ArrowDown restarted navigation from the very top of the canvas
            const offsetParentSpy = vi.spyOn(HTMLElement.prototype, "offsetParent", "get").mockReturnValue(document.body)
            const wrapper = mountBlockEditor()
            const vm = wrapper.vm as unknown as {focusedId?: string}
            vm.focusedId = "log_task"
            await wrapper.vm.$nextTick()

            // When
            windowKeydown({key: "Backspace"})
            await flushPromises()
            await wrapper.vm.$nextTick()

            // Then — log_task is gone and its next sibling took the focus ring
            expect(vm.focusedId).toBe("http_task")
            offsetParentSpy.mockRestore()
        })

        describe("native Tab harmony (roving tabindex)", () => {
            let offsetParentSpy: ReturnType<typeof vi.spyOn>

            beforeEach(() => {
                offsetParentSpy = vi.spyOn(HTMLElement.prototype, "offsetParent", "get").mockReturnValue(document.body)
            })

            afterEach(() => {
                offsetParentSpy.mockRestore()
            })

            it("makes only the focused card a Tab stop", async () => {
                // Given
                const wrapper = mount(BlockEditor, {...makeConfig(), attachTo: document.body})
                const vm = wrapper.vm as unknown as {focusedId?: string}
                const cardTabindexes = () => wrapper.findAll("[data-test='block-card']")
                    .map(c => `${c.attributes("data-block-id")}:${c.attributes("tabindex")}`)
                expect(cardTabindexes()).toEqual(["log_task:-1", "http_task:-1"])

                // When
                vm.focusedId = "http_task"
                await wrapper.vm.$nextTick()

                // Then
                expect(cardTabindexes()).toEqual(["log_task:-1", "http_task:0"])
                wrapper.unmount()
            })

            it("keeps the canvas container as the Tab entry point only while nothing is focused", async () => {
                // Given
                const wrapper = mount(BlockEditor, {...makeConfig(), attachTo: document.body})
                const vm = wrapper.vm as unknown as {focusedId?: string}
                const canvas = wrapper.find(".block-editor-canvas")
                expect(canvas.attributes("tabindex")).toBe("0")

                // When — the container receives Tab focus, it delegates to the first card
                await canvas.trigger("focus")
                await wrapper.vm.$nextTick()
                await wrapper.vm.$nextTick()

                // Then — first navigable stop (the empty Triggers sentinel here)
                expect(vm.focusedId).toBe("__section:triggers")
                expect(canvas.attributes("tabindex")).toBe("-1")
                wrapper.unmount()
            })

            it("syncs the focus ring from real DOM focus (Tab or click landing on a card)", async () => {
                // Given
                const wrapper = mount(BlockEditor, {...makeConfig(), attachTo: document.body})
                const vm = wrapper.vm as unknown as {focusedId?: string}

                // When — real focus lands on a card, as native Tab or a click would
                const card = wrapper.findAll("[data-test='block-card']")[1]
                ;(card.element as HTMLElement).focus()
                await wrapper.vm.$nextTick()

                // Then — the virtual ring follows, so shortcuts act on what was reached
                expect(vm.focusedId).toBe("http_task")
                wrapper.unmount()
            })

            it("moves real DOM focus when navigating with the arrows", async () => {
                // Given
                const wrapper = mount(BlockEditor, {...makeConfig(), attachTo: document.body})

                // When
                windowKeydown({key: "ArrowDown"})
                await wrapper.vm.$nextTick()
                await wrapper.vm.$nextTick()

                // Then — document.activeElement moved with the ring, so a
                // follow-up native Tab continues from the focused card
                expect(document.activeElement?.getAttribute("data-block-id")).toBe("__section:triggers")
                wrapper.unmount()
            })
        })

        it("does not fire Delete when the event target is an input", async () => {
            // Given
            const wrapper = mountBlockEditor()
            await wrapper.find("[data-test='block-card']").trigger("click")
            await wrapper.vm.$nextTick()
            const originalLength = 2
            const inputEl = document.createElement("input")
            document.body.appendChild(inputEl)

            // When — simulate event from an input element
            const event = new KeyboardEvent("keydown", {key: "Delete", bubbles: true, cancelable: true})
            Object.defineProperty(event, "target", {value: inputEl})
            window.dispatchEvent(event)
            await flushPromises()
            await wrapper.vm.$nextTick()
            document.body.removeChild(inputEl)

            // Then — no deletion
            expect(confirmMock).not.toHaveBeenCalled()
            const {flowYamlUtils} = await import("@kestra-io/topology")
            const parsed = flowYamlUtils.parse(mockFlowYaml.value)
            expect(parsed.tasks).toHaveLength(originalLength)
        })

        it("Alt+ArrowDown reorders the selected task to the second position", async () => {
            // Given
            const wrapper = mountBlockEditor()
            await wrapper.find("[data-test='block-card']").trigger("click")
            await wrapper.vm.$nextTick()

            // When
            windowKeydown({key: "ArrowDown", altKey: true})
            await wrapper.vm.$nextTick()

            // Then
            const {flowYamlUtils} = await import("@kestra-io/topology")
            const parsed = flowYamlUtils.parse(mockFlowYaml.value)
            expect(parsed.tasks).toHaveLength(2)
            expect(parsed.tasks[0].id).toBe("http_task")
            expect(parsed.tasks[1].id).toBe("log_task")
        })

        it("Alt+ArrowUp reorders the selected task to the first position", async () => {
            // Given
            const wrapper = mountBlockEditor()
            const cards = wrapper.findAll("[data-test='block-card']")
            await cards[1].trigger("click")
            await wrapper.vm.$nextTick()

            // When
            windowKeydown({key: "ArrowUp", altKey: true})
            await wrapper.vm.$nextTick()

            // Then
            const {flowYamlUtils} = await import("@kestra-io/topology")
            const parsed = flowYamlUtils.parse(mockFlowYaml.value)
            expect(parsed.tasks[0].id).toBe("http_task")
            expect(parsed.tasks[1].id).toBe("log_task")
        })

        it("Alt+ArrowDown reorders the canvas-focused task without opening it in the dock", async () => {
            // Given — j/k canvas focus only (regression: reorder previously only acted
            // on a block already open in the dock, so navigating with the keyboard and
            // pressing Alt+ArrowDown right away silently did nothing).
            const offsetParentSpy = vi.spyOn(HTMLElement.prototype, "offsetParent", "get").mockReturnValue(document.body)
            const wrapper = mountBlockEditor()
            windowKeydown({key: "ArrowDown"}) // focus the empty Triggers section
            await wrapper.vm.$nextTick()
            windowKeydown({key: "ArrowDown"}) // focus log_task
            await wrapper.vm.$nextTick()

            // When
            windowKeydown({key: "ArrowDown", altKey: true})
            await wrapper.vm.$nextTick()

            // Then
            const {flowYamlUtils} = await import("@kestra-io/topology")
            const parsed = flowYamlUtils.parse(mockFlowYaml.value)
            expect(parsed.tasks[0].id).toBe("http_task")
            expect(parsed.tasks[1].id).toBe("log_task")
            offsetParentSpy.mockRestore()
        })

        it("two consecutive Alt+ArrowDown moves a nested block twice (same block, not different ones)", async () => {
            // Given — YAML_WITH_FLOWABLE has if_block with nested_a in then[0] and nested_b in else[0]
            // We need a then lane with at least 3 tasks so we can verify two moves from index 0
            mockFlowYaml.value = `
id: my_flow
namespace: company.team
tasks:
  - id: if_block
    type: io.kestra.plugin.core.flow.If
    condition: "{{ true }}"
    then:
      - id: then_a
        type: io.kestra.plugin.core.log.Log
      - id: then_b
        type: io.kestra.plugin.core.log.Log
      - id: then_c
        type: io.kestra.plugin.core.log.Log
`.trim()
            const wrapper = mountBlockEditor()
            const cluster = wrapper.findComponent({name: "FlowableClusterCard"})

            // Simulate selecting then_a (index 0 in then lane) via openNestedEdit path
            await cluster.vm.$emit("select", "tasks[0].then[0]")
            await wrapper.vm.$nextTick()
            await wrapper.vm.$nextTick()

            // When — first move: then_a goes from [0] to [1]
            windowKeydown({key: "ArrowDown", altKey: true})
            await wrapper.vm.$nextTick()

            // When — second consecutive move: then_a should go from [1] to [2]
            windowKeydown({key: "ArrowDown", altKey: true})
            await wrapper.vm.$nextTick()

            // Then — then_a has moved twice and is now at index 2
            const {flowYamlUtils} = await import("@kestra-io/topology")
            const parsed = flowYamlUtils.parse(mockFlowYaml.value)
            expect(parsed.tasks[0].then[0].id).toBe("then_b")
            expect(parsed.tasks[0].then[1].id).toBe("then_c")
            expect(parsed.tasks[0].then[2].id).toBe("then_a")
        })
    })

    describe("footer status bar", () => {
        function footerText() {
            return wrapper!.find("[data-test='block-editor-footer']").text()
        }

        it("names the canvas and offers the base hints when nothing is focused", () => {
            // Given

            // When
            wrapper = mount(BlockEditor, makeConfig())

            // Then
            const text = footerText()
            expect(text).toContain("Canvas")
            expect(text).toContain("This help")
            expect(text).toContain("Move selection")
            expect(text).toContain("Open selected block")
            expect(text).toContain("Insert a block after selection")
            expect(text).toContain("Command menu (contextual)")
            expect(text).not.toContain("Insert a block before selection")
            expect(text).not.toContain("Reorder up / down")
        })

        it("renders each hint's keys through their symbol, deduplicated", () => {
            // Given
            wrapper = mount(BlockEditor, makeConfig())

            // When
            const keys = wrapper.findAll("[data-test='block-editor-footer'] kbd").map(k => k.text())

            // Then — Meta+Shift+p and Control+Shift+p collapse to a single symbol
            expect(keys).toEqual(["?", "↑", "↓", "↵", "a", "⌘⇧P"])
        })

        it("offers insert-before and reorder once a real block is focused", async () => {
            // Given
            wrapper = mount(BlockEditor, makeConfig())
            const vm = wrapper.vm as unknown as {focusedId?: string}

            // When
            vm.focusedId = "log_task"
            await wrapper.vm.$nextTick()

            // Then
            const text = footerText()
            expect(text).toContain("Selected · log_task")
            expect(text).toContain("Insert a block before selection")
            expect(text).toContain("Reorder up / down")
        })

        it("withholds insert-before and reorder on an empty section's sentinel", async () => {
            // Given
            wrapper = mount(BlockEditor, makeConfig())
            const vm = wrapper.vm as unknown as {focusedId?: string}

            // When — the sentinel stands for the section itself, not a block
            vm.focusedId = "__section:triggers"
            await wrapper.vm.$nextTick()

            // Then
            const text = footerText()
            expect(text).toContain("Selected · Triggers")
            expect(text).not.toContain("Insert a block before selection")
            expect(text).not.toContain("Reorder up / down")
        })

        it("names the lane when an empty lane sentinel inside a flowable is focused", async () => {
            // Given
            mockFlowYaml.value = YAML_WITH_FLOWABLE
            wrapper = mount(BlockEditor, makeConfig())
            const vm = wrapper.vm as unknown as {focusedId?: string}

            // When
            vm.focusedId = "__lane:tasks[1].errors"
            await wrapper.vm.$nextTick()

            // Then
            expect(footerText()).toContain("Selected · Errors")
        })

        it("swaps to the list hints while the picker is open", async () => {
            // Given
            wrapper = mount(BlockEditor, makeConfig())

            // When
            await wrapper.find("[data-test='block-editor-add-task']").trigger("click")
            await wrapper.vm.$nextTick()

            // Then
            const text = footerText()
            expect(text).toContain("Inserting")
            expect(text).toContain("navigate")
            expect(text).toContain("add")
            expect(text).toContain("close")
            expect(text).not.toContain("This help")
        })
    })

    describe("delete undo badge", () => {
        it("announces the deleted block and restores it when undo is clicked", async () => {
            // Given
            wrapper = mount(BlockEditor, makeConfig())
            const before = mockFlowYaml.value

            // When
            await wrapper.find("[data-test='block-card-delete']").trigger("click")
            await wrapper.vm.$nextTick()

            // Then — the badge names the block that went away
            const badge = wrapper.find("[data-test='block-editor-undo']")
            expect(badge.exists()).toBe(true)
            expect(wrapper.find(".block-editor-undo-label").text()).toBe("log_task deleted")
            expect(mockFlowYaml.value).not.toBe(before)

            // When
            await badge.trigger("click")
            await wrapper.vm.$nextTick()

            // Then
            expect(mockFlowYaml.value).toBe(before)
            expect(wrapper.find("[data-test='block-editor-undo']").exists()).toBe(false)
        })

        it("dismisses the badge on the next edit instead of leaving it stale", async () => {
            // Given
            wrapper = mount(BlockEditor, makeConfig())
            await wrapper.find("[data-test='block-card-delete']").trigger("click")
            await wrapper.vm.$nextTick()
            expect(wrapper.find("[data-test='block-editor-undo']").exists()).toBe(true)

            // When — any further edit supersedes the pending undo
            const vm = wrapper.vm as unknown as {picker: {insertTask: (fqcn: string) => void}}
            vm.picker.insertTask("io.kestra.plugin.core.log.Log")
            await wrapper.vm.$nextTick()

            // Then
            expect(wrapper.find("[data-test='block-editor-undo']").exists()).toBe(false)
        })

        it("keeps undoing back through several edits", async () => {
            // Given
            wrapper = mount(BlockEditor, makeConfig())
            const original = mockFlowYaml.value
            const vm = wrapper.vm as unknown as {picker: {insertTask: (fqcn: string) => void}; performUndo: () => void}
            vm.picker.insertTask("io.kestra.plugin.core.log.Log")
            await wrapper.vm.$nextTick()
            const afterFirst = mockFlowYaml.value
            vm.picker.insertTask("io.kestra.plugin.core.http.Request")
            await wrapper.vm.$nextTick()

            // When
            vm.performUndo()
            await wrapper.vm.$nextTick()

            // Then
            expect(mockFlowYaml.value).toBe(afterFirst)

            // When
            vm.performUndo()
            await wrapper.vm.$nextTick()

            // Then
            expect(mockFlowYaml.value).toBe(original)
        })
    })

    describe("task picker views", () => {
        function pickerEl() {
            return document.body.querySelector("[data-test='block-editor-picker']")
        }

        async function openPicker() {
            await wrapper!.find("[data-test='block-editor-add-task']").trigger("click")
            await wrapper!.vm.$nextTick()
        }

        it("lists the suggested types for the section it was opened on", async () => {
            // Given
            wrapper = mount(BlockEditor, makeConfig())

            // When
            await openPicker()

            // Then — both mock plugins are suggested for the tasks section
            const names = [...pickerEl()!.querySelectorAll(".block-editor-picker-name")].map(n => n.textContent)
            expect(names).toEqual(["Log", "If"])
            expect(pickerEl()!.textContent).toContain("Inserting into Tasks")
        })

        it("remembers an inserted type and lists it under the recent tab", async () => {
            // Given
            wrapper = mount(BlockEditor, makeConfig())
            await openPicker()

            // When
            const vm = wrapper.vm as unknown as {picker: {insertTask: (fqcn: string) => void}}
            vm.picker.insertTask("io.kestra.plugin.core.flow.If")
            await wrapper.vm.$nextTick()

            // Then
            expect(JSON.parse(localStorage.getItem("blockEditor.recentTaskTypes") ?? "[]"))
                .toEqual(["io.kestra.plugin.core.flow.If"])

            // When — a later open reads recents back from storage
            await openPicker()
            pickerEl()!.querySelector<HTMLElement>("[data-test='block-editor-picker-tab-recent']")!.click()
            await wrapper.vm.$nextTick()

            // Then
            const names = [...pickerEl()!.querySelectorAll(".block-editor-picker-name")].map(n => n.textContent)
            expect(names).toEqual(["If"])
        })

        it("drills into an app group and walks back out", async () => {
            // Given
            wrapper = mount(BlockEditor, makeConfig())
            await openPicker()

            // When
            pickerEl()!.querySelector<HTMLElement>("[data-test='block-editor-picker-tab-apps']")!.click()
            await wrapper.vm.$nextTick()

            // Then — one group, carrying its action count
            const groups = [...pickerEl()!.querySelectorAll(".block-editor-picker-app")]
            expect(groups).toHaveLength(1)
            expect(groups[0].textContent).toContain("Core")
            expect(groups[0].textContent).toContain("2 actions")

            // When
            ;(groups[0] as HTMLElement).click()
            await wrapper.vm.$nextTick()

            // Then
            const names = [...pickerEl()!.querySelectorAll(".block-editor-picker-name")].map(n => n.textContent)
            expect(names).toEqual(["Log", "If"])
            expect(pickerEl()!.textContent).toContain("All apps")

            // When
            pickerEl()!.querySelector<HTMLElement>(".block-editor-picker-back")!.click()
            await wrapper.vm.$nextTick()

            // Then
            expect(pickerEl()!.querySelectorAll(".block-editor-picker-app")).toHaveLength(1)
        })

        it("reports how many search matches are hidden beyond the cap", async () => {
            vi.useFakeTimers()
            const extras = Array.from({length: 60}, (_, i) => ({
                cls: `io.kestra.plugin.core.bulk.Bulk${i}`,
                title: `Bulk ${i}`,
            }))
            mockPlugins[0].tasks.push(...extras)
            try {
                // Given
                wrapper = mount(BlockEditor, makeConfig())
                await openPicker()
                const vm = wrapper.vm as unknown as {picker: {taskPickerSearch: {value: string}; pickerHiddenCount: {value: number}}}

                // When
                vm.picker.taskPickerSearch.value = "bulk"
                await wrapper.vm.$nextTick()
                vi.advanceTimersByTime(200)
                await wrapper.vm.$nextTick()

                // Then — 60 matches, 50 shown
                expect(vm.picker.pickerHiddenCount.value).toBe(10)
                expect(pickerEl()!.querySelectorAll(".block-editor-picker-row")).toHaveLength(50)
                expect(pickerEl()!.textContent).toContain("10 more, refine your search")
            } finally {
                mockPlugins[0].tasks.splice(mockPlugins[0].tasks.length - extras.length, extras.length)
                vi.useRealTimers()
            }
        })

        it("inserts the highlighted entry when Enter is pressed in the list", async () => {
            // Given
            wrapper = mount(BlockEditor, makeConfig())
            await openPicker()
            const picker = pickerEl()!

            // When — the first entry starts highlighted, so one step down lands on If
            picker.dispatchEvent(new KeyboardEvent("keydown", {key: "ArrowDown", bubbles: true, cancelable: true}))
            await wrapper.vm.$nextTick()
            picker.dispatchEvent(new KeyboardEvent("keydown", {key: "Enter", bubbles: true, cancelable: true}))
            await wrapper.vm.$nextTick()

            // Then
            const {flowYamlUtils} = await import("@kestra-io/topology")
            const parsed = flowYamlUtils.parse(mockFlowYaml.value) as {tasks: {type: string}[]}
            expect(parsed.tasks).toHaveLength(3)
            expect(parsed.tasks[2].type).toBe("io.kestra.plugin.core.flow.If")
            expect(pickerEl()).toBeNull()
        })
    })

    describe("shortcuts help", () => {
        it("lists every keymap group but hides the clear binding", async () => {
            // Given
            wrapper = mount(BlockEditor, makeConfig())

            // When
            window.dispatchEvent(new KeyboardEvent("keydown", {key: "?", bubbles: true, cancelable: true}))
            await wrapper.vm.$nextTick()

            // Then
            const help = wrapper.find(".block-editor-shortcuts")
            expect(help.exists()).toBe(true)
            const headings = wrapper.findAll(".block-editor-shortcuts-heading").map(h => h.text())
            expect(headings).toEqual(["Navigate", "Insert", "Edit", "Global"])
            expect(help.text()).toContain("Move selection")
            expect(help.text()).toContain("Save flow")
            expect(help.text()).not.toContain("Close / clear selection")
        })

        it("hides the footer while the help overlay is open", async () => {
            // Given
            wrapper = mount(BlockEditor, makeConfig())
            expect(wrapper.find("[data-test='block-editor-footer']").exists()).toBe(true)

            // When
            window.dispatchEvent(new KeyboardEvent("keydown", {key: "?", bubbles: true, cancelable: true}))
            await wrapper.vm.$nextTick()

            // Then
            expect(wrapper.find("[data-test='block-editor-footer']").exists()).toBe(false)
        })
    })

    describe("command menu context", () => {
        async function openCommandMenu() {
            const vm = wrapper!.vm as unknown as {commandMenuOpen: boolean}
            vm.commandMenuOpen = true
            await wrapper!.vm.$nextTick()
            return wrapper!.findComponent({name: "BlockCommandMenu"})
        }

        it("describes the flow itself when no block is focused", async () => {
            // Given
            wrapper = mount(BlockEditor, makeConfig())

            // When
            const menu = await openCommandMenu()

            // Then
            expect(menu.props("contextLabel")).toBe("context: flow")
            const titles = (menu.props("items") as {title: string}[]).map(i => i.title)
            expect(titles).toContain("Insert task at end of Tasks")
        })

        it("describes the focused block and targets it for insertion", async () => {
            // Given
            wrapper = mount(BlockEditor, makeConfig())
            const vm = wrapper.vm as unknown as {focusedId?: string}
            vm.focusedId = "log_task"

            // When
            const menu = await openCommandMenu()

            // Then
            expect(menu.props("contextLabel")).toBe("context: log_task")
            const titles = (menu.props("items") as {title: string}[]).map(i => i.title)
            expect(titles).toContain("Insert task after log_task")
        })

        it("describes an empty section by its label when its sentinel is focused", async () => {
            // Given
            wrapper = mount(BlockEditor, makeConfig())
            const vm = wrapper.vm as unknown as {focusedId?: string}
            vm.focusedId = "__section:triggers"

            // When
            const menu = await openCommandMenu()

            // Then
            expect(menu.props("contextLabel")).toBe("context: Triggers")
            const titles = (menu.props("items") as {title: string}[]).map(i => i.title)
            expect(titles).toContain("Insert task in Triggers")
        })
    })
})
