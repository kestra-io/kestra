import {describe, it, expect, vi, afterEach, beforeEach} from "vitest"
import {defineComponent, h} from "vue"
import {flushPromises, type VueWrapper} from "@vue/test-utils"

// The component watches the route, so the stub has to be reactive - a plain object
// makes Vue warn about an invalid watch source on every mount.
vi.mock("vue-router", async () => {
    const {reactive} = await import("vue")
    const route = reactive({
        name: "flows/update/triggers",
        params: {namespace: "company.team", id: "my-flow"},
        query: {},
    })
    return {
        useRoute: () => route,
        useRouter: () => ({push: vi.fn()}),
    }
})

const flowStore = vi.hoisted(() => ({
    flow: {
        namespace: "company.team",
        id: "my-flow",
        triggers: [
            {id: "trigger-a", type: "io.kestra.plugin.core.trigger.Schedule"},
            {id: "trigger-b", type: "io.kestra.plugin.core.trigger.Schedule"},
        ],
    },
}))

vi.mock("../../../../src/stores/flow", () => ({
    useFlowStore: () => flowStore,
}))

// Shared so a test can revoke a permission; the executions link is gated on EXECUTION:VIEW,
// which is all this suite grants - the trigger actions stay hidden, as with no user at all.
const permissions = vi.hoisted(() => ({
    isAllowed: (resource: string, action: string) => resource === "EXECUTION" && action === "VIEW",
}))

vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({user: {isAllowed: (...args: [string, string]) => permissions.isAllowed(...args)}}),
}))

vi.mock("override/stores/misc", () => ({
    useMiscStore: () => ({configs: {edition: "OSS"}}),
}))

vi.mock("../../../../src/utils/toast", () => ({
    useToast: () => ({saved: vi.fn(), success: vi.fn(), error: vi.fn(), confirm: vi.fn()}),
}))

// trigger-b already ran once (hence the current execution), trigger-a never did
// (hence no state row and no current execution).
vi.mock("../../../../src/utils/triggers", () => ({
    searchTriggersForFlow: vi.fn().mockResolvedValue({
        results: [
            {
                triggerId: "trigger-b",
                namespace: "company.team",
                flowId: "my-flow",
                executionId: "execution-for-b",
            },
        ],
    }),
}))

vi.mock("../../../../src/stores/productTour", () => ({
    useProductTourStore: () => ({isGuidedActive: false}),
}))

import FlowTriggers from "../../../../src/components/flows/FlowTriggers.vue"
import {i18nMount} from "../../i18nMount"

const RouterLinkStub = defineComponent({
    name: "RouterLink",
    props: {to: {type: Object, default: undefined}},
    setup: (_, {slots}) => () => h("a", {href: "#"}, slots.default?.()),
})

function mountTriggersTab() {
    const wrapper = i18nMount(FlowTriggers, {
        props: {embed: true},
        // TestEventDialog pulls editor bindings (real Pinia stores) even while
        // closed; it is irrelevant to the executions link.
        global: {stubs: {RouterLink: RouterLinkStub, TestEventDialog: true}},
    })
    mounted.push(wrapper)
    return wrapper
}

// The real data table schedules DOM reads after render; unmount before teardown
// so they never fire into a detached jsdom document.
const mounted: VueWrapper[] = []
beforeEach(() => {
    vi.useFakeTimers()
    localStorage.clear()
    permissions.isAllowed = (resource: string, action: string) => resource === "EXECUTION" && action === "VIEW"
})
afterEach(() => {
    while (mounted.length) mounted.pop()?.unmount()
    localStorage.clear()
    vi.useRealTimers()
})

// Issue #12784: each trigger row links to the flow executions tab pre-filtered
// on that trigger, so the list shows every execution the trigger created.
describe("FlowTriggers - executions link", () => {
    const executionsLinks = (wrapper: VueWrapper) =>
        wrapper.findAllComponents(RouterLinkStub)
            .filter(link => link.attributes("data-test") === "trigger-executions-link")

    it("links to the flow executions tab filtered on the row's trigger", async () => {
        const wrapper = mountTriggersTab()
        await flushPromises()

        const links = executionsLinks(wrapper)
        expect(links).toHaveLength(2)

        expect(links[1].props("to")).toEqual({
            name: "flows/update/executions",
            params: {tenant: undefined, namespace: "company.team", id: "my-flow"},
            query: {"filters[triggerId][EQUALS]": "trigger-b"},
        })
    })

    it("falls back to the trigger definition id when the trigger has no state row yet", async () => {
        const wrapper = mountTriggersTab()
        await flushPromises()

        expect(executionsLinks(wrapper)[0].props("to")).toEqual({
            name: "flows/update/executions",
            params: {tenant: undefined, namespace: "company.team", id: "my-flow"},
            query: {"filters[triggerId][EQUALS]": "trigger-a"},
        })
    })

    it("keeps linking the current execution to its execution page", async () => {
        const wrapper = mountTriggersTab()
        await flushPromises()

        const currentExecution = wrapper.findAllComponents(RouterLinkStub)
            .find(link => (link.props("to") as {name?: string} | undefined)?.name === "executions/update")

        expect(currentExecution?.props("to")).toMatchObject({
            params: {namespace: "company.team", flowId: "my-flow", id: "execution-for-b"},
        })
    })

    it("renders a labelled anchor, so the executions list opens in a new tab like any link", async () => {
        const wrapper = mountTriggersTab()
        await flushPromises()

        const link = executionsLinks(wrapper)[0]
        expect(link.element.tagName).toBe("A")
        expect(link.attributes("aria-label")).toBe("executions")
    })

    it("hides the link from a user who cannot read the flow's executions", async () => {
        permissions.isAllowed = () => false

        const wrapper = mountTriggersTab()
        await flushPromises()

        expect(executionsLinks(wrapper)).toHaveLength(0)
    })
})
