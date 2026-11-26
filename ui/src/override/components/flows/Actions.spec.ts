import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {computed} from "vue"
import {type VueWrapper} from "@vue/test-utils"
import KestraDesignSystem from "@kestra-io/design-system"

const publish = vi.fn().mockResolvedValue(undefined)

const routeState = {tab: "edit"}
const flowState = {deleted: false, exists: true, isCreating: false, isDraftOnly: false}
const editorState = {isAllowedEdit: true}
const playgroundState = {enabled: false}

vi.mock("vue-router", () => ({
    useRoute: () => ({params: {tab: routeState.tab}, query: {}}),
    useRouter: () => ({push: vi.fn()}),
}))

vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({user: {isAllowed: () => true}}),
}))

vi.mock("../../../stores/flow", () => ({
    useFlowStore: () => ({
        flow: flowState.exists
            ? {id: "f", namespace: "ns", draft: true, deleted: flowState.deleted, source: "id: f\nnamespace: ns\n"}
            : undefined,
        isCreating: flowState.isCreating,
        isDraftOnly: flowState.isDraftOnly,
        createFlow: vi.fn(),
    }),
}))

vi.mock("../../../stores/playground", () => ({
    usePlaygroundStore: () => playgroundState,
}))

vi.mock("../../../stores/unsavedChanges", () => ({
    useUnsavedChangesStore: () => ({unsavedChange: false}),
}))

vi.mock("../../../stores/dashboard.ts", () => ({
    useDashboardStore: () => ({getUserDashboardStorageKey: () => "key"}),
}))

vi.mock("../../../stores/logs", () => ({
    useLogsStore: () => ({logs: undefined}),
}))

vi.mock("../../../utils/toast", () => ({
    useToast: () => ({confirm: vi.fn(), error: vi.fn(), deleted: vi.fn(), saved: vi.fn(), success: vi.fn()}),
}))

// Actions.vue only wires the composable's output to the template (visibility/disabled/click) -
// stub the composable so this test targets that wiring in isolation.
vi.mock("../../../components/flows/useFlowEditorActions", () => ({
    useFlowEditorActions: () => ({
        haveChange: computed(() => false),
        canSave: computed(() => false),
        hasErrors: computed(() => false),
        isReadOnly: computed(() => false),
        get isAllowedEdit() {
            return editorState.isAllowedEdit
        },
        isDraft: computed(() => true),
        isPlaygroundAllowed: false,
        publish,
        saveAsDraft: vi.fn(),
        saveAndExecute: vi.fn(),
        exportYaml: vi.fn(),
        copyFlow: vi.fn(),
        deleteFlow: vi.fn(),
        togglePlayground: vi.fn(),
    }),
}))

import Actions from "./Actions.vue"
import {i18nMount} from "../../../../tests/unit/i18nMount"

const messages = {
    restore: "Restore",
    "edit flow": "Edit flow",
    "delete logs": "Delete logs",
    save_and_execute: "Save & Execute",
    copy: "Copy",
    flow_export: "Export flow",
    delete: "Delete",
    save_draft: "Save draft",
    publish: "Publish",
    actions: "Actions",
}

// The unit project shares one jsdom per worker, so a wrapper left mounted keeps the teleported
// poppers of its two dropdowns attached to <body> and fails the whole file (tests/unit/leakGuard.ts).
let activeWrapper: VueWrapper | undefined

afterEach(() => {
    activeWrapper?.unmount()
    activeWrapper = undefined
})

function mountActions() {
    activeWrapper = i18nMount(Actions, {
        messages,
        global: {
            plugins: [KestraDesignSystem],
            stubs: {TriggerFlow: true, Dashboards: true, FlowPlaygroundToggle: true},
        },
    })

    return activeWrapper
}

function findButtonByText(wrapper: ReturnType<typeof mountActions>, text: string) {
    return wrapper.findAll("button").find(btn => btn.text().trim() === text)
}

function findExecute(wrapper: ReturnType<typeof mountActions>) {
    return wrapper.find("trigger-flow-stub")
}


describe("Actions.vue — the quick action pair is the same shape on every tab", () => {
    beforeEach(() => {
        vi.clearAllMocks()
        routeState.tab = "edit"
        flowState.deleted = false
        flowState.exists = true
        flowState.isCreating = false
        flowState.isDraftOnly = false
        playgroundState.enabled = false
        editorState.isAllowedEdit = true
    })

    it("pairs the save-family control with Execute on the editor tab", () => {
        const wrapper = mountActions()

        expect(findButtonByText(wrapper, "Publish")).toBeDefined()
        expect(findButtonByText(wrapper, "Edit flow")).toBeUndefined()
        expect(findExecute(wrapper).exists()).toBe(true)
    })

    it.each(["overview", "executions", "logs", "revisions", "triggers", "apps", "audit-logs"])(
        "pairs Edit flow with Execute on the %s tab",
        (tab) => {
            routeState.tab = tab

            const wrapper = mountActions()

            expect(findButtonByText(wrapper, "Edit flow")).toBeDefined()
            expect(findExecute(wrapper).exists()).toBe(true)
        },
    )

    it.each(["edit", "overview", "executions"])("offers no Execute on the %s tab of a flow with only drafts", (tab) => {
        routeState.tab = tab
        flowState.isDraftOnly = true

        expect(findExecute(mountActions()).exists()).toBe(false)
    })

    it("keeps the Playground's Run all tasks on a flow with only drafts", () => {
        routeState.tab = "edit"
        flowState.isDraftOnly = true
        playgroundState.enabled = true

        expect(findExecute(mountActions()).exists()).toBe(true)
    })

    it("offers no Edit flow on the create page, where there is no flow to edit yet", () => {
        // Given — the create-flow landing: creation started, but no flow exists yet
        routeState.tab = "edit"
        flowState.exists = false
        flowState.isCreating = true
        editorState.isAllowedEdit = false

        // When
        const wrapper = mountActions()

        // Then — Edit flow used to render here and navigate to an undefined flow
        expect(findButtonByText(wrapper, "Edit flow")).toBeUndefined()
    })

    it("promotes Restore to the primary slot on a deleted flow, and offers no Execute", () => {
        routeState.tab = "overview"
        flowState.deleted = true

        const wrapper = mountActions()

        expect(findButtonByText(wrapper, "Restore")).toBeDefined()
        expect(findExecute(wrapper).exists()).toBe(false)
    })
})
