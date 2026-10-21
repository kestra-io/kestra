import {afterEach, beforeEach, describe, expect, test, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {defineComponent, ref} from "vue"
import {i18nMount} from "../../../tests/unit/i18nMount"
import {createPinia, setActivePinia} from "pinia"
import * as YAML_UTILS from "@kestra-io/topology/flow-yaml-utils"
import type {ParsedFlow} from "../../stores/flow"

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

vi.mock("../../stores/flow", () => ({useFlowStore: () => flowStore}))
vi.mock("../../stores/executions", () => ({
    useExecutionsStore: () => ({
        execution: undefined,
        subflowsExecutions: {},
        flowGraph: undefined,
        progressEvents: [],
        loadAugmentedGraph: vi.fn(),
    }),
}))
vi.mock("../../stores/playground", () => ({
    usePlaygroundStore: () => ({enabled: false, readyToStart: false, runUntilTask: vi.fn()}),
}))
vi.mock("../../stores/core", () => ({useCoreStore: () => ({message: undefined})}))
vi.mock("../../stores/plugins", () => ({
    usePluginsStore: () => ({icons: {}, loadIcon: vi.fn(), fetchIcons: vi.fn(), flowSchema: undefined}),
}))
vi.mock("override/stores/auth", () => ({useAuthStore: () => ({user: undefined})}))
vi.mock("vue-router", () => ({
    useRoute: () => ({params: {}, query: {}}),
    useRouter: () => ({push: vi.fn(), replace: vi.fn()}),
}))
vi.mock("../../composables/useEditorBindings", () => ({useEditorBindings: () => ({})}))
vi.mock("../../remoteComponents/useFederatedModule", () => ({
    useFederatedModule: () => ({
        RemoteComponent: defineComponent({render: () => null}),
        taskAdditionalInfoRemote: ref({}),
        manifestReady: ref(true),
        componentTypeFor: (task?: {type?: string}) => task?.type ?? "",
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

import LowCodeEditor from "./LowCodeEditor.vue"

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


// A task nested in a flowable is still its own node, so its plugin UI must be requested too. This was lost once in a conflict merge.
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
        flowStore.flowParsed = YAML_UTILS.parse<ParsedFlow>(FLOW)

        const wrapper = i18nMount(LowCodeEditor, {
            props: {flowGraph: {nodes: [], edges: [], clusters: []}, isReadOnly: true},
            global: {stubs: {teleport: true}},
        })
        await flushPromises()

        expect(requestedTypes).toContain("io.kestra.plugin.core.flow.WorkingDirectory")
        expect(requestedTypes).toContain("io.kestra.plugin.core.log.Log")
        wrapper.unmount()
    })
})
