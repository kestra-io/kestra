import {describe, it, expect, vi, afterEach, beforeEach} from "vitest"
import {defineComponent, h} from "vue"
import {flushPromises, type VueWrapper} from "@vue/test-utils"

// Shared across calls so the navigation tests can assert on it; a fresh spy per
// useRouter() call would be unreachable from the test body.
const routerPush = vi.hoisted(() => vi.fn())

vi.mock("vue-router", () => ({
    useRoute: () => ({
        name: "flows/update/triggers",
        params: {namespace: "company.team", id: "my-flow"},
        query: {},
    }),
    useRouter: () => ({push: routerPush}),
}))

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

vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({user: undefined}),
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
    routerPush.mockClear()
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
})
afterEach(() => {
    while (mounted.length) mounted.pop()?.unmount()
    localStorage.clear()
    vi.useRealTimers()
})

// Issue #12784: each trigger row links to the flow executions tab pre-filtered
// on that trigger, so the list shows every execution the trigger created.
describe("FlowTriggers — executions link", () => {
    it("navigates to the flow executions tab filtered on the clicked row's trigger", async () => {
        const wrapper = mountTriggersTab()
        await flushPromises()

        const links = wrapper.findAll("[data-test=\"trigger-executions-link\"]")
        expect(links).toHaveLength(2)

        await links[1].trigger("click")
        expect(routerPush).toHaveBeenCalledWith({
            name: "flows/update/executions",
            params: {tenant: undefined, namespace: "company.team", id: "my-flow"},
            query: {"filters[triggerId][EQUALS]": "trigger-b"},
        })
    })

    it("falls back to the trigger definition id when the trigger has no state row yet", async () => {
        const wrapper = mountTriggersTab()
        await flushPromises()

        const links = wrapper.findAll("[data-test=\"trigger-executions-link\"]")

        await links[0].trigger("click")
        expect(routerPush).toHaveBeenCalledWith({
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

    it("renders the executions button as a natively keyboard-activatable button", async () => {
        const wrapper = mountTriggersTab()
        await flushPromises()

        // Enter/Space activation is platform behavior for a native button, which
        // jsdom does not synthesize, so this pins the structural facts it depends on:
        // a real, enabled, labelled submit-neutral button in normal tab order.
        const button = wrapper.find("[data-test=\"trigger-executions-link\"]")
        expect(button.element.tagName).toBe("BUTTON")
        expect(button.attributes("type")).toBe("button")
        expect(button.attributes("disabled")).toBeUndefined()
        expect(button.attributes("aria-label")).toBe("executions")
    })
})
