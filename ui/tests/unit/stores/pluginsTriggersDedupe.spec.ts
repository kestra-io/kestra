import {beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"

vi.mock("override/utils/route", () => ({
    apiUrlWithoutTenants: () => "/api/v1",
}))

vi.mock("override/stores/misc", () => ({
    useMiscStore: () => ({configs: {}}),
}))

vi.mock("../../../src/utils/tabTracking", () => ({
    trackPluginDocumentationView: vi.fn(),
}))

const listTriggerPluginsFn = vi.fn()

vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({get: vi.fn(), post: vi.fn()}),
}))

vi.mock("@kestra-io/kestra-sdk/plugins", () => ({
    listTriggerPlugins: (...args: unknown[]) => listTriggerPluginsFn(...args),
}))

const trigger = (type: string) => ({
    type,
    name: "RealtimeTrigger",
    pluginTitle: "TCP",
    description: null,
    group: "realtime",
    ee: false,
    icon: "",
    deprecated: null,
})

const TEST_TIMEOUT_MS = 20_000

describe("plugins store trigger catalog", () => {
    beforeEach(() => {
        vi.resetModules()
        setActivePinia(createPinia())
        listTriggerPluginsFn.mockReset()
    })

    it("should keep a single entry per trigger type when the API returns duplicates", {timeout: TEST_TIMEOUT_MS}, async () => {
        // Regression test for https://github.com/kestra-io/kestra/issues/18419: a plugin installed
        // in several versions makes the API repeat its trigger types, and the duplicated types
        // corrupt the triggers grid, which keys its cards by type.
        listTriggerPluginsFn.mockResolvedValue({
            results: [
                trigger("io.kestra.plugin.tcp.RealtimeTrigger"),
                trigger("io.kestra.plugin.tcp.RealtimeTrigger"),
                trigger("io.kestra.plugin.udp.RealtimeTrigger"),
            ],
            total: 3,
        })
        const {usePluginsStore} = await import("../../../src/stores/plugins")

        const triggers = await usePluginsStore().listTriggers()

        expect(triggers.map(t => t.type)).toEqual([
            "io.kestra.plugin.tcp.RealtimeTrigger",
            "io.kestra.plugin.udp.RealtimeTrigger",
        ])
    })
})
