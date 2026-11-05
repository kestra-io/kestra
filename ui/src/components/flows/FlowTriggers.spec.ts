import {describe, it, expect, vi, afterEach, beforeEach} from "vitest"
import {defineComponent, h} from "vue"
import {flushPromises, mount, type ComponentMountingOptions, type VueWrapper} from "@vue/test-utils"
import {createPinia} from "pinia"

const searchLogs = vi.hoisted(() => vi.fn())

vi.mock("@kestra-io/kestra-sdk/logs", () => ({
    searchLogs,
    deleteLogsFromFlow: vi.fn(),
}))

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

vi.mock("../../stores/flow", () => ({
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

vi.mock("../../utils/toast", () => ({
    useToast: () => ({saved: vi.fn(), success: vi.fn(), error: vi.fn(), confirm: vi.fn()}),
}))

// trigger-b already ran once (hence the current execution), trigger-a never did
// (hence no state row and no current execution). A test may add fields to trigger-b's state.
const triggerBState = vi.hoisted(() => ({extra: {} as Record<string, unknown>}))

vi.mock("../../utils/triggers", async (importOriginal) => ({
    ...await importOriginal<typeof import("../../utils/triggers")>(),
    searchTriggersForFlow: vi.fn().mockImplementation(() => Promise.resolve({
        results: [
            {
                triggerId: "trigger-b",
                namespace: "company.team",
                flowId: "my-flow",
                executionId: "execution-for-b",
                ...triggerBState.extra,
            },
        ],
    })),
}))

vi.mock("../../stores/productTour", () => ({
    useProductTourStore: () => ({isGuidedActive: false}),
}))

import FlowTriggers from "./FlowTriggers.vue"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "@kestra-io/design-system"

function i18nMount<T>(component: T, {global: globalOptions = {}, ...options}: ComponentMountingOptions<T> = {}) {
    const i18n = createI18n({legacy: false, locale: "en", missingWarn: false, fallbackWarn: false, warnHtmlMessage: false, messages: {en: {}}})
    return mount(component, {
        ...options,
        global: {...globalOptions, plugins: [i18n, KestraDesignSystem, ...(globalOptions.plugins ?? [])]},
    } as ComponentMountingOptions<T>)
}

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
        global: {plugins: [createPinia()], stubs: {RouterLink: RouterLinkStub, TestEventDialog: true}},
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
    sessionStorage.clear()
    searchLogs.mockReset().mockResolvedValue({results: [], total: 0})
    permissions.isAllowed = (resource: string, action: string) => resource === "EXECUTION" && action === "VIEW"
    triggerBState.extra = {}
})
afterEach(() => {
    while (mounted.length) mounted.pop()?.unmount()
    localStorage.clear()
    sessionStorage.clear()
    vi.useRealTimers()
})

// kestra-ee#10290: a trigger blocked by governance says so in place of its next evaluation date.
describe("FlowTriggers - governance block", () => {
    const blockedTooltip = (wrapper: VueWrapper, dataTest = "trigger-blocked") =>
        wrapper.findAllComponents({name: "KsTooltip"}).find(tooltip => tooltip.find(`[data-test="${dataTest}"]`).exists())

    it("shows the block with a message on the tag and next to the id", async () => {
        triggerBState.extra = {blockedBy: {reason: "POLICY", ids: ["deny-log"], message: "Log plugin is not allowed by policy"}}

        const wrapper = mountTriggersTab()
        await flushPromises()

        expect(blockedTooltip(wrapper)?.props("content")).toBeTruthy()
        expect(blockedTooltip(wrapper, "trigger-blocked-icon")?.props("content")).toBeTruthy()
    })

    it("shows no block for a trigger governance accepts", async () => {
        const wrapper = mountTriggersTab()
        await flushPromises()

        expect(wrapper.find("[data-test=\"trigger-blocked\"]").exists()).toBe(false)
        expect(wrapper.find("[data-test=\"trigger-blocked-icon\"]").exists()).toBe(false)
    })
})

// Issue #12784: each trigger row links to the flow executions tab pre-filtered
// on that trigger, so the list shows every execution the trigger created.
