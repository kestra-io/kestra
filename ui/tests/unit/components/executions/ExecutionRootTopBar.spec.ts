import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "@kestra-io/design-system"

const executionState = {current: "SUCCESS"}
const permissions = {execute: true}

vi.mock("vue-router", () => ({
    useRoute: () => ({params: {namespace: "ns", flowId: "f", id: "e"}, query: {}}),
    useRouter: () => ({push: vi.fn()}),
    RouterLink: {template: "<a><slot /></a>"},
}))

vi.mock("../../../../src/stores/executions", () => ({
    useExecutionsStore: () => ({
        execution: {id: "e", namespace: "ns", flowId: "f", state: executionState, labels: []},
    }),
}))

vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({
        user: {isAllowed: (_resource: string, act: string) => (act === "EXECUTE" ? permissions.execute : true)},
    }),
}))

import ExecutionRootTopBar from "../../../../src/components/executions/ExecutionRootTopBar.vue"

const i18n = createI18n({
    legacy: false,
    locale: "en",
    missingWarn: false,
    fallbackWarn: false,
    messages: {en: {actions: "Actions"}},
})

function mountTopBar() {
    return mount(ExecutionRootTopBar, {
        props: {routeInfo: {title: "e", breadcrumb: []}},
        global: {
            plugins: [i18n, KestraDesignSystem],
            stubs: {
                TopNavBar: {template: "<div><slot name=\"actions\" /></div>"},
                TriggerFlow: {template: "<button>Execute</button>"},
                Restart: {props: ["isReplay"], template: "<button>{{ isReplay ? 'Replay' : 'Restart' }}</button>"},
                Pause: {template: "<button>Pause</button>"},
                Resume: {template: "<button>Resume</button>"},
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
