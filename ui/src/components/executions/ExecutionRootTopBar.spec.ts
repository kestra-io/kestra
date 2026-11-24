import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import KestraDesignSystem from "@kestra-io/design-system"

const executionState = {current: "SUCCESS"}
const permissions = {execute: true}
const storeState = vi.hoisted(() => ({taskRunList: [] as {id?: string; taskId: string; state: {current: string}}[], flow: undefined as unknown, reviewAllowed: true}))
const routeMeta = vi.hoisted(() => ({value: {} as Record<string, unknown>}))
const mobile = vi.hoisted(() => ({value: false}))
const loadedFlow = {id: "f", namespace: "ns", revision: 1, tasks: []}

vi.mock("vue-router", () => ({
    useRoute: () => ({params: {namespace: "ns", flowId: "f", id: "e"}, query: {}, meta: routeMeta.value}),
    useRouter: () => ({push: vi.fn()}),
    RouterLink: {template: "<a><slot /></a>"},
}))

vi.mock("../../composables/useIsMobile", async () => {
    const {ref} = await import("vue")
    return {useIsMobile: () => ref(mobile.value)}
})

vi.mock("../../stores/executions", () => ({
    useExecutionsStore: () => ({
        execution: {id: "e", namespace: "ns", flowId: "f", flowRevision: 1, state: executionState, labels: [], taskRunList: storeState.taskRunList},
        flow: storeState.flow,
        isReviewAllowed: () => Promise.resolve(storeState.reviewAllowed),
    }),
}))

// Hoisted: the mock factory below reads it eagerly, before plain top-level consts exist.
const extraOverflowAction = vi.hoisted(() => ({template: "<button>Create case</button>"}))
vi.mock("override/components/executions/executionsExtensions", () => ({
    getExtraColumns: () => [],
    cellComponents: {},
    bulkActionComponents: [],
    overflowActionComponents: [extraOverflowAction],
}))

vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({
        user: {isAllowed: (_resource: string, act: string) => (act === "EXECUTE" ? permissions.execute : true)},
    }),
}))

import ExecutionRootTopBar from "./ExecutionRootTopBar.vue"
import {i18nMount} from "../../../tests/unit/i18nMount"

beforeEach(() => {
    executionState.current = "SUCCESS"
    permissions.execute = true
    storeState.taskRunList = []
    storeState.flow = loadedFlow
    storeState.reviewAllowed = true
    routeMeta.value = {}
    mobile.value = false
})

function mountTopBar(stubs: Record<string, unknown> = {}) {
    return i18nMount(ExecutionRootTopBar, {
        messages: {actions: "Actions"},
        props: {routeInfo: {title: "e", breadcrumb: []}},
        global: {
            plugins: [KestraDesignSystem],
            stubs: {
                TopNavBar: {template: "<div><slot name=\"actions\" /></div>"},
                TriggerFlow: {template: "<button>Execute</button>"},
                Restart: {props: ["isReplay"], template: "<button>{{ isReplay ? 'Replay' : 'Restart' }}</button>"},
                Pause: {template: "<button>Pause</button>"},
                Resume: {template: "<button>Resume</button>"},
                Review: {template: "<button>Review</button>"},
                CancelApproval: {template: "<button>Cancel approval</button>"},
                ResumeFromBreakpoint: {template: "<button>Resume from breakpoint</button>"},
                Kill: {template: "<button>Kill</button>"},
                Unqueue: {template: "<button>Unqueue</button>"},
                ForceRun: {template: "<button>Force run</button>"},
                Api: {template: "<button>API</button>"},
                Delete: {template: "<button>Delete</button>"},
                EditFlow: {template: "<button>Edit Flow</button>"},
                ...stubs,
            },
        },
    })
}

function visibleButtons(wrapper: ReturnType<typeof mountTopBar>) {
    return wrapper.findAll("button").map(button => button.text().trim()).filter(Boolean)
}

describe("ExecutionRootTopBar — Execute is the primary on every execution state", () => {
    beforeEach(() => {
        executionState.current = "SUCCESS"
        permissions.execute = true
    })

    it.each([
        ["CREATED", "Pause"],
        ["RUNNING", "Pause"],
        ["PAUSED", "Resume"],
        ["PAUSING", "Edit Flow"],
        ["BREAKPOINT", "Resume from breakpoint"],
        ["FAILED", "Restart"],
        ["SUCCESS", "Replay"],
        ["KILLED", "Replay"],
        ["WARNING", "Replay"],
        ["CANCELLED", "Replay"],
    ])("offers %s the %s secondary next to Execute", (current, secondary) => {
        executionState.current = current

        const buttons = visibleButtons(mountTopBar())

        expect(buttons).toContain(secondary)
        expect(buttons).toContain("Execute")
    })

    it("falls back to Edit Flow while the execution has no state yet", () => {
        executionState.current = ""

        const buttons = visibleButtons(mountTopBar())

        expect(buttons).toContain("Edit Flow")
        expect(buttons).toContain("Execute")
    })

    it("shows a single secondary, never two", () => {
        executionState.current = "FAILED"

        const buttons = visibleButtons(mountTopBar())

        expect(buttons.filter(label => label !== "Execute" && label !== "Actions")).toEqual(["Restart"])
    })

    it("omits Execute rather than disabling it when the user cannot execute the flow", () => {
        permissions.execute = false

        const buttons = visibleButtons(mountTopBar())

        expect(buttons).not.toContain("Execute")
        expect(buttons).toContain("Replay")
    })
})

describe("ExecutionRootTopBar — the overflow menu", () => {
    beforeEach(() => {
        executionState.current = "SUCCESS"
        permissions.execute = true
    })

    it("keeps Delete last, after any edition-specific action contributed to the menu", async () => {
        const wrapper = mountTopBar()

        await wrapper.find("button[aria-label=\"Actions\"]").trigger("click")
        await new Promise(resolve => setTimeout(resolve))

        const labels = Array.from(document.querySelectorAll("button"))
            .map(button => button.textContent?.trim())
            .filter((label): label is string => Boolean(label))

        expect(labels).toContain("Create case")
        expect(labels.indexOf("Create case")).toBeLessThan(labels.indexOf("Delete"))
        expect(labels.at(-1)).toBe("Delete")
    })
})

describe("ExecutionRootTopBar — an Approval paused beside another pause", () => {
    beforeEach(() => {
        executionState.current = "PAUSED"
        storeState.taskRunList = [
            {id: "tr-approval", taskId: "approval", state: {current: "PAUSED"}},
            {id: "tr-pause", taskId: "pause", state: {current: "PAUSED"}},
        ]
        storeState.flow = {...loadedFlow, tasks: [{id: "approval", type: "io.kestra.plugin.core.flow.Approval"}, {id: "pause", type: "io.kestra.plugin.core.flow.Pause"}]}
    })

    async function overflowLabels(wrapper = mountTopBar()) {
        await wrapper.find("button[aria-label=\"Actions\"]").trigger("click")
        await new Promise(resolve => setTimeout(resolve))
        return Array.from(document.querySelectorAll("button")).map(button => button.textContent?.trim())
    }

    it("keeps Resume reachable for the plain pause while Review is offered", async () => {
        const wrapper = mountTopBar()
        await flushPromises()

        expect(visibleButtons(wrapper)).toContain("Review")
        expect(await overflowLabels(wrapper)).toContain("Resume")
    })

    it("offers no Resume when only the Approval is paused", async () => {
        storeState.taskRunList = [{id: "tr-approval", taskId: "approval", state: {current: "PAUSED"}}]

        expect(await overflowLabels()).not.toContain("Resume")
    })
})

describe("ExecutionRootTopBar — a pause whose flow is not loaded yet", () => {
    async function allLabels(wrapper: ReturnType<typeof mountTopBar>) {
        await wrapper.find("button[aria-label=\"Actions\"]").trigger("click")
        await new Promise(resolve => setTimeout(resolve))
        return Array.from(document.querySelectorAll("button")).map(button => button.textContent?.trim())
    }

    it.each([
        ["no flow", undefined],
        ["another revision", {...loadedFlow, revision: 2}],
    ])("offers neither Resume nor Review with %s in the store", async (_case, flow) => {
        executionState.current = "PAUSED"
        storeState.flow = flow

        const labels = await allLabels(mountTopBar())

        expect(labels).not.toContain("Resume")
        expect(labels).not.toContain("Review")
    })
})

describe("ExecutionRootTopBar — a pausing execution", () => {
    it("offers no Pause, since it is already pausing", async () => {
        executionState.current = "PAUSING"
        const wrapper = mountTopBar()

        await wrapper.find("button[aria-label=\"Actions\"]").trigger("click")
        await new Promise(resolve => setTimeout(resolve))

        expect(Array.from(document.querySelectorAll("button")).map(button => button.textContent?.trim())).not.toContain("Pause")
    })
})

describe("ExecutionRootTopBar — an execution paused on an Approval", () => {
    const approvalFlow = {...loadedFlow, tasks: [{id: "approval", type: "io.kestra.plugin.core.flow.Approval"}, {id: "pause", type: "io.kestra.plugin.core.flow.Pause"}]}

    beforeEach(() => {
        executionState.current = "PAUSED"
        storeState.taskRunList = [{id: "tr-approval", taskId: "approval", state: {current: "PAUSED"}}]
        storeState.flow = approvalFlow
    })

    async function labels(wrapper: ReturnType<typeof mountTopBar>) {
        await flushPromises()
        const visible = visibleButtons(wrapper)
        await wrapper.find("button[aria-label=\"Actions\"]").trigger("click")
        await new Promise(resolve => setTimeout(resolve))
        const menu = Array.from(document.querySelectorAll("button")).map(button => button.textContent?.trim())
        return {visible, all: [...visible, ...menu]}
    }

    it("leaves Review to the banner on a tab that shows it, with Execute and Edit Flow in the top bar", async () => {
        routeMeta.value = {tab: "overview", reviewInBanner: true}

        const {visible, all} = await labels(mountTopBar())

        expect(visible).toContain("Execute")
        expect(visible).toContain("Edit Flow")
        expect(all).not.toContain("Review")
    })

    it("keeps Review in the bottom bar on mobile, where no banner is shown", async () => {
        routeMeta.value = {tab: "gantt", reviewInBanner: true}
        mobile.value = true

        const wrapper = mountTopBar({
            BottomActionBar: {template: "<div data-test=\"bottom-action-bar\"><slot /></div>"},
            NavBarActionsDropdown: {template: "<div />"},
        })
        await flushPromises()

        expect(wrapper.find("[data-test='bottom-action-bar']").text()).toContain("Review")
    })

    it("shows Review in place of Execute on a tab without the banner, with Execute in the menu", async () => {
        routeMeta.value = {tab: "outputs"}

        const {visible, all} = await labels(mountTopBar())

        expect(visible).toContain("Review")
        expect(visible).not.toContain("Execute")
        expect(all).toContain("Execute")
        expect(all.filter(label => label === "Review")).toHaveLength(1)
    })

    it("keeps Execute in the top bar for a user who cannot review the request", async () => {
        routeMeta.value = {tab: "outputs"}
        storeState.reviewAllowed = false

        const {visible} = await labels(mountTopBar())

        expect(visible).toContain("Execute")
        expect(visible).not.toContain("Review")
    })

    it("keeps Resume and Execute for a plain pause on a tab without the banner", async () => {
        routeMeta.value = {tab: "outputs"}
        storeState.taskRunList = [{id: "tr-pause", taskId: "pause", state: {current: "PAUSED"}}]

        const {visible} = await labels(mountTopBar())

        expect(visible).toContain("Resume")
        expect(visible).toContain("Execute")
        expect(visible).not.toContain("Review")
    })
})
