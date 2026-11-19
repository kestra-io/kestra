import {beforeEach, describe, expect, it, vi} from "vitest"
import KestraDesignSystem from "@kestra-io/design-system"

const executionState = {current: "SUCCESS"}
const permissions = {execute: true}
const storeState = vi.hoisted(() => ({taskRunList: [] as {taskId: string; state: {current: string}}[], flow: undefined as unknown}))
const loadedFlow = {id: "f", namespace: "ns", revision: 1, tasks: []}

vi.mock("vue-router", () => ({
    useRoute: () => ({params: {namespace: "ns", flowId: "f", id: "e"}, query: {}}),
    useRouter: () => ({push: vi.fn()}),
    RouterLink: {template: "<a><slot /></a>"},
}))

vi.mock("../../stores/executions", () => ({
    useExecutionsStore: () => ({
        execution: {id: "e", namespace: "ns", flowId: "f", flowRevision: 1, state: executionState, labels: [], taskRunList: storeState.taskRunList},
        flow: storeState.flow,
    }),
}))

// Hoisted: the mock factory below reads it eagerly, before plain top-level consts exist.
const extraOverflowAction = vi.hoisted(() => ({template: "<button>Create case</button>"}))
vi.mock("override/components/executions/executionsExtensions", () => ({
    getExtraColumns: () => [],
    cellComponents: {},
    bulkActionComponents: [],
    reviewActionComponent: undefined,
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
})

function mountTopBar() {
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
            {taskId: "approval", state: {current: "PAUSED"}},
            {taskId: "pause", state: {current: "PAUSED"}},
        ]
        storeState.flow = {...loadedFlow, tasks: [{id: "approval", type: "io.kestra.plugin.core.flow.Approval"}, {id: "pause", type: "io.kestra.plugin.core.flow.Pause"}]}
    })

    async function overflowLabels(wrapper = mountTopBar()) {
        await wrapper.find("button[aria-label=\"Actions\"]").trigger("click")
        await new Promise(resolve => setTimeout(resolve))
        return Array.from(document.querySelectorAll("button")).map(button => button.textContent?.trim())
    }

    it("keeps Resume reachable for the plain pause while Review stays the secondary", async () => {
        const wrapper = mountTopBar()

        expect(visibleButtons(wrapper)).toContain("Review")
        expect(await overflowLabels(wrapper)).toContain("Resume")
    })

    it("offers no Resume when only the Approval is paused", async () => {
        storeState.taskRunList = [{taskId: "approval", state: {current: "PAUSED"}}]

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
