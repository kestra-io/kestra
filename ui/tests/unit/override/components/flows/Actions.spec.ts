import {beforeEach, describe, expect, it, vi} from "vitest"
import {computed} from "vue"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "@kestra-io/design-system"

const publishDraft = vi.fn().mockResolvedValue("saved")

const routeState = {tab: "edit"}
const flowState = {deleted: false}

vi.mock("vue-router", () => ({
    useRoute: () => ({params: {tab: routeState.tab}, query: {}}),
    useRouter: () => ({push: vi.fn()}),
}))

vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({user: {isAllowed: () => true}}),
}))

vi.mock("../../../../../src/stores/flow", () => ({
    useFlowStore: () => ({
        flow: {id: "f", namespace: "ns", draft: true, deleted: flowState.deleted, source: "id: f\nnamespace: ns\n"},
        isCreating: false,
        createFlow: vi.fn(),
    }),
}))

vi.mock("../../../../../src/stores/unsavedChanges", () => ({
    useUnsavedChangesStore: () => ({unsavedChange: false}),
}))

vi.mock("../../../../../src/stores/dashboard.ts", () => ({
    useDashboardStore: () => ({getUserDashboardStorageKey: () => "key"}),
}))

vi.mock("../../../../../src/stores/logs", () => ({
    useLogsStore: () => ({logs: undefined}),
}))

vi.mock("../../../../../src/utils/toast", () => ({
    useToast: () => ({confirm: vi.fn(), error: vi.fn(), deleted: vi.fn(), saved: vi.fn(), success: vi.fn()}),
}))

// Actions.vue only wires the composable's output to the template (visibility/disabled/click) -
// stub the composable so this test targets that wiring in isolation.
vi.mock("../../../../../src/components/flows/useFlowEditorActions", () => ({
    useFlowEditorActions: () => ({
        haveChange: false,
        hasFlowSourceChange: false,
        canSave: false,
        hasErrors: false,
        isReadOnly: false,
        isAllowedEdit: true,
        // The real composable returns computed refs; `isDraft` is read from script (not just
        // auto-unwrapped in a template), so the mock has to be a ref for that read to work.
        isDraft: computed(() => true),
        isPlaygroundEnabled: false,
        isPlaygroundAllowed: false,
        save: vi.fn(),
        saveAsDraft: vi.fn(),
        publishDraft,
        saveAndExecute: vi.fn(),
        exportYaml: vi.fn(),
        copyFlow: vi.fn(),
        deleteFlow: vi.fn(),
        togglePlayground: vi.fn(),
    }),
}))

import Actions from "../../../../../src/override/components/flows/Actions.vue"

const i18n = createI18n({
    legacy: false,
    locale: "en",
    missingWarn: false,
    fallbackWarn: false,
    messages: {
        en: {
            restore: "Restore",
            "edit flow": "Edit flow",
            "delete logs": "Delete logs",
            save_and_execute: "Save & Execute",
            copy: "Copy",
            flow_export: "Export flow",
            delete: "Delete",
            save: "Save",
            save_as_draft: "Save as draft",
            publish: "Publish",
            actions: "Actions",
        },
    },
})

function mountActions() {
    return mount(Actions, {
        global: {
            plugins: [i18n, KestraDesignSystem],
            stubs: {TriggerFlow: true, Dashboards: true, FlowPlaygroundToggle: true},
        },
    })
}

function findButtonByText(wrapper: ReturnType<typeof mountActions>, text: string) {
    return wrapper.findAll("button").find(btn => btn.text().trim() === text)
}

function findExecute(wrapper: ReturnType<typeof mountActions>) {
    return wrapper.find("trigger-flow-stub")
}

describe("Actions.vue — publish a draft flow", () => {
    // publishDraft is module-level, so its call count carries between tests.
    beforeEach(() => {
        vi.clearAllMocks()
        routeState.tab = "edit"
        flowState.deleted = false
    })

    it("shows an enabled Publish action for an unchanged draft flow, and clicking it publishes", async () => {
        const wrapper = mountActions()

        const publishButton = findButtonByText(wrapper, "Publish")
        expect(publishButton).toBeDefined()
        expect(publishButton!.attributes("disabled")).toBeUndefined()

        await publishButton!.trigger("click")

        expect(publishDraft).toHaveBeenCalledTimes(1)
    })
})

describe("Actions.vue — the quick action pair is the same shape on every tab", () => {
    beforeEach(() => {
        vi.clearAllMocks()
        routeState.tab = "edit"
        flowState.deleted = false
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

    it("promotes Restore to the primary slot on a deleted flow, and offers no Execute", () => {
        routeState.tab = "overview"
        flowState.deleted = true

        const wrapper = mountActions()

        expect(findButtonByText(wrapper, "Restore")).toBeDefined()
        expect(findExecute(wrapper).exists()).toBe(false)
    })
})
