import {afterEach, beforeEach, describe, expect, test, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {defineComponent, ref} from "vue"
import {createI18n} from "vue-i18n"
import {createPinia, setActivePinia} from "pinia"
import * as YAML_UTILS from "@kestra-io/topology/flow-yaml-utils"

type TaskType = {cls: string, version: string | undefined}

const requestedTypes = vi.hoisted(() => [] as string[])

const flowStore = vi.hoisted(() => ({
    flowYaml: "",
    flowParsed: undefined as unknown,
    flow: undefined,
    flowErrors: undefined,
    save: vi.fn(),
    loadGraphFromSource: vi.fn(),
}))

vi.mock("../../../../src/stores/flow", () => ({useFlowStore: () => flowStore}))
vi.mock("../../../../src/stores/executions", () => ({
    useExecutionsStore: () => ({
        execution: undefined,
        subflowsExecutions: {},
        flowGraph: undefined,
        progressEvents: [],
        loadAugmentedGraph: vi.fn(),
    }),
}))
vi.mock("../../../../src/stores/playground", () => ({
    usePlaygroundStore: () => ({enabled: false, readyToStart: false, runUntilTask: vi.fn()}),
}))
vi.mock("../../../../src/stores/core", () => ({useCoreStore: () => ({message: undefined})}))
vi.mock("../../../../src/stores/plugins", () => ({
    usePluginsStore: () => ({icons: {}, loadIcon: vi.fn(), fetchIcons: vi.fn(), flowSchema: undefined}),
}))
vi.mock("override/stores/auth", () => ({useAuthStore: () => ({user: undefined})}))
vi.mock("vue-router", () => ({
    useRoute: () => ({params: {}, query: {}}),
    useRouter: () => ({push: vi.fn(), replace: vi.fn()}),
}))
vi.mock("../../../../src/composables/useEditorBindings", () => ({useEditorBindings: () => ({})}))
vi.mock("../../../../src/remoteComponents/useFederatedModule", () => ({
    useFederatedModule: () => ({
        RemoteComponent: defineComponent({render: () => null}),
        taskAdditionalInfoRemote: ref({}),
        manifestReady: ref(true),
        resolveRemoteComponent: (types: TaskType[]) => {
            requestedTypes.push(...types.map((t) => t.cls))
            return Promise.resolve()
        },
    }),
}))
vi.mock("@kestra-io/topology", async (importOriginal) => ({
    ...(await importOriginal<typeof import("@kestra-io/topology")>()),
    Topology: defineComponent({name: "Topology", render: () => null}),
    NodeMenu: defineComponent({name: "NodeMenu", render: () => null}),
}))

import LowCodeEditor from "../../../../src/components/inputs/LowCodeEditor.vue"

const FLOW = `
id: nested
namespace: company.team
tasks:
  - id: wdir
    type: io.kestra.plugin.core.flow.WorkingDirectory
    tasks:
      - id: log
        type: io.kestra.plugin.core.log.Log
`

// A task nested in a flowable is still its own node, so its plugin UI must be requested too (kestra-io/kestra#21688).
describe("LowCodeEditor plugin UI lookup", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
    })

    afterEach(() => {
        requestedTypes.length = 0
        localStorage.clear()
    })

    test("requests the plugin UI of a task nested in a flowable", async () => {
        flowStore.flowYaml = FLOW
        flowStore.flowParsed = YAML_UTILS.parse(FLOW)

        const wrapper = mount(LowCodeEditor, {
            props: {flowGraph: {nodes: [], edges: [], clusters: []}, isReadOnly: true},
            global: {
                plugins: [createI18n({legacy: false, locale: "en", missingWarn: false, fallbackWarn: false})],
                stubs: {teleport: true},
            },
        })
        await flushPromises()

        expect(requestedTypes).toContain("io.kestra.plugin.core.flow.WorkingDirectory")
        expect(requestedTypes).toContain("io.kestra.plugin.core.log.Log")
        wrapper.unmount()
    })
})
