import {afterEach, beforeEach, expect, it, vi} from "vitest"
import {flushPromises, mount, type ComponentMountingOptions} from "@vue/test-utils"
import {createPinia} from "pinia"
import {createMemoryHistory, createRouter} from "vue-router"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "@kestra-io/design-system"

function i18nMount<T>(component: T, {global: globalOptions = {}, ...options}: ComponentMountingOptions<T> = {}) {
    const i18n = createI18n({legacy: false, locale: "en", missingWarn: false, fallbackWarn: false, warnHtmlMessage: false, messages: {en: {}}})
    return mount(component, {
        ...options,
        global: {...globalOptions, plugins: [i18n, KestraDesignSystem, ...(globalOptions.plugins ?? [])]},
    } as ComponentMountingOptions<T>)
}

vi.mock("@kestra-io/kestra-sdk/logs", () => ({searchLogs: vi.fn().mockResolvedValue({results: [], total: 0}), deleteLogsFromFlow: vi.fn()}))
vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({user: {isAllowed: () => true, hasAny: () => true, hasAnyAction: () => false}}),
}))
vi.mock("../../../stores/flow", () => ({useFlowStore: () => ({})}))
vi.mock("../../../stores/executions", () => ({useExecutionsStore: () => ({})}))
vi.mock("../../../utils/triggers", async (importOriginal) => ({
    ...await importOriginal<typeof import("../../../utils/triggers")>(),
    searchTriggers: vi.fn().mockResolvedValue({
        total: 2,
        results: [
            {namespace: "team.a", flowId: "fa", cron: "0 7 * * 1"},
            {namespace: "team.b", flowId: "fb", cron: "0 9 * * *"},
        ].map(({namespace, flowId, cron}) => ({
            trigger: {id: "daily", type: "io.kestra.plugin.core.trigger.Schedule", cron},
            state: {namespace, flowId, triggerId: "daily"},
        })),
    }),
}))

import TriggersManage from "./TriggersManage.vue"

beforeEach(() => {
    vi.useFakeTimers()
    localStorage.clear()
    sessionStorage.clear()
})

afterEach(() => {
    localStorage.clear()
    sessionStorage.clear()
    document.title = ""
    vi.useRealTimers()
})

it("shows the details of the clicked trigger when another flow has a trigger with the same id", async () => {
    const router = createRouter({
        history: createMemoryHistory(),
        routes: [{name: "admin/triggers", path: "/:tenant/triggers", component: {template: "<div/>"}}],
    })
    await router.push({name: "admin/triggers", params: {tenant: "main"}})
    await router.isReady()
    const wrapper = i18nMount(TriggersManage, {
        global: {
            plugins: [createPinia(), router],
            stubs: {
                FlowRun: true, TopNavBar: true, AddTriggerModal: true, TestEventDialog: true,
                KsDropdown: {template: "<div><slot /><slot name=\"dropdown\" /></div>"},
                KsDropdownMenu: {template: "<div><slot /></div>"},
                KsDropdownItem: {template: "<button class=\"menu-item\" @click=\"$emit('click')\"><slot /></button>"},
                KsDrawer: {template: "<div class=\"details\"><slot /></div>"},
                Vars: {props: ["data"], template: "<pre>{{ JSON.stringify(data) }}</pre>"},
            },
        },
    })
    await flushPromises()

    const details = wrapper.findAll(".menu-item").filter(item => item.text() === "details")
    await details[1].trigger("click")
    await flushPromises()

    expect(wrapper.get(".details").text()).toContain("0 9 * * *")
})
