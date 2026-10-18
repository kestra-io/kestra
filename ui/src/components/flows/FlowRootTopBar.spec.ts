import {beforeEach, describe, expect, it, vi} from "vitest"
import {computed} from "vue"
import {createPinia} from "pinia"
import Alert from "vue-material-design-icons/Alert.vue"
import Lock from "vue-material-design-icons/Lock.vue"
import Badge from "../global/Badge.vue"

const flowState = {
    deleted: false,
    draft: false,
    exists: true,
    namespace: "company.team",
    id: "my-flow",
}

const permissions = {
    update: true,
}

vi.mock("vue-router", () => ({
    useRoute: () => ({params: {tab: "overview"}, query: {}}),
    useRouter: () => ({push: vi.fn(), replace: vi.fn()}),
}))

vi.mock("../../stores/flow", () => ({
    useFlowStore: () => ({
        flow: flowState.exists
            ? {
                id: flowState.id,
                namespace: flowState.namespace,
                deleted: flowState.deleted,
                draft: flowState.draft,
                source: "id: my-flow\nnamespace: company.team\n",
            }
            : undefined,
        isCreating: false,
        createFlow: vi.fn(),
    }),
}))

vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({
        user: {
            isAllowed: (_resource: string, act: string, _namespace?: string) => {
                if (act === "UPDATE") return permissions.update
                return true
            },
        },
    }),
}))

vi.mock("../../stores/unsavedChanges", () => ({
    useUnsavedChangesStore: () => ({unsavedChange: false}),
}))

vi.mock("../../stores/dashboard.ts", () => ({
    useDashboardStore: () => ({getUserDashboardStorageKey: () => "key"}),
}))

vi.mock("../../stores/logs", () => ({
    useLogsStore: () => ({logs: undefined}),
}))

vi.mock("../../utils/toast", () => ({
    useToast: () => ({confirm: vi.fn(), error: vi.fn(), deleted: vi.fn(), saved: vi.fn(), success: vi.fn()}),
}))

vi.mock("../../components/flows/useFlowEditorActions", () => ({
    useFlowEditorActions: () => ({
        haveChange: false,
        canSave: false,
        hasErrors: false,
        isReadOnly: false,
        get isAllowedEdit() {
            return permissions.update
        },
        isDraft: computed(() => flowState.draft),
        isPlaygroundAllowed: false,
        save: vi.fn(),
        saveAsDraft: vi.fn(),
        publishDraft: vi.fn(),
        saveAndExecute: vi.fn(),
        exportYaml: vi.fn(),
        copyFlow: vi.fn(),
        deleteFlow: vi.fn(),
    }),
}))

import FlowRootTopBar from "./FlowRootTopBar.vue"
import {i18nMount} from "../../../tests/unit/i18nMount"

const defaultRouteInfo = {
    title: "my-flow",
    breadcrumb: [{label: "Flows", href: "/flows"}],
}

const NavBarStub = {
    name: "NavBar",
    props: ["title", "breadcrumb", "bookmarkLabel"],
    template: "<div class=\"nav-bar-stub\"><slot name=\"title\" /><slot name=\"actions\" /></div>",
}

function mountFlowRootTopBar(routeInfo = defaultRouteInfo) {
    return i18nMount(FlowRootTopBar, {
        messages: {
            deleted_label: "Deleted",
            draft: "Draft",
            "plugins.beta": "Beta",
            "edit flow": "Edit flow",
        },
        props: {
            routeInfo,
        },
        global: {
            plugins: [createPinia()],
            stubs: {
                NavBar: NavBarStub,
                TopNavBar: NavBarStub,
                TriggerFlow: true,
                Dashboards: true,
                FlowPlaygroundToggle: true,
            },
        },
    })
}

describe("FlowRootTopBar", () => {
    beforeEach(() => {
        flowState.deleted = false
        flowState.draft = false
        flowState.exists = true
        permissions.update = true
    })

    it("shows the deleted badge only for a deleted flow", () => {
        // Not deleted
        let wrapper = mountFlowRootTopBar()
        expect(wrapper.findComponent(Alert).exists()).toBe(false)
        expect(wrapper.text()).not.toContain("Deleted")
        expect(wrapper.find(".body-color").exists()).toBe(false)

        // Deleted
        flowState.deleted = true
        wrapper = mountFlowRootTopBar()
        expect(wrapper.findComponent(Alert).exists()).toBe(true)
        expect(wrapper.text()).toContain("Deleted")
        expect(wrapper.find(".body-color").exists()).toBe(true)
    })

    it("shows the draft badge only for a draft flow", () => {
        // Not draft
        let wrapper = mountFlowRootTopBar()
        const badges = wrapper.findAllComponents(Badge)
        expect(badges.some((b) => b.props("label") === "Draft")).toBe(false)

        // Draft
        flowState.draft = true
        wrapper = mountFlowRootTopBar()
        const draftBadges = wrapper.findAllComponents(Badge)
        expect(draftBadges.some((b) => b.props("label") === "Draft")).toBe(true)
    })

    it("can show both deleted and draft badges at once", () => {
        flowState.deleted = true
        flowState.draft = true

        const wrapper = mountFlowRootTopBar()
        expect(wrapper.findComponent(Alert).exists()).toBe(true)
        expect(wrapper.text()).toContain("Deleted")

        const badges = wrapper.findAllComponents(Badge)
        expect(badges.some((b) => b.props("label") === "Draft")).toBe(true)
    })

    it("shows edit actions only when the user is allowed to update that flow's namespace", () => {
        permissions.update = true
        const wrapper = mountFlowRootTopBar()

        const editButton = wrapper.findAll("button").find((btn) => btn.text().includes("Edit flow"))
        expect(editButton).toBeDefined()
        expect(wrapper.findComponent(Lock).exists()).toBe(false)
    })

    it("shows the lock rather than edit actions for a user with no permission", () => {
        permissions.update = false
        const wrapper = mountFlowRootTopBar()

        expect(wrapper.findComponent(Lock).exists()).toBe(true)
        const editButton = wrapper.findAll("button").find((btn) => btn.text().includes("Edit flow"))
        expect(editButton).toBeUndefined()
    })

    it("passes the route title and breadcrumb through to the nav bar", () => {
        const customRouteInfo = {
            title: "Custom Flow Title",
            breadcrumb: [
                {label: "Namespaces", href: "/namespaces"},
                {label: "company.team", href: "/flows/company.team"},
            ],
            bookmarkLabel: "Custom Flow",
        }

        const wrapper = mountFlowRootTopBar(customRouteInfo)
        const navBar = wrapper.findComponent(NavBarStub)

        expect(navBar.exists()).toBe(true)
        expect(navBar.props("title")).toBe("Custom Flow Title")
        expect(navBar.props("breadcrumb")).toEqual(customRouteInfo.breadcrumb)
        expect(navBar.props("bookmarkLabel")).toBe("Custom Flow")
    })
})
