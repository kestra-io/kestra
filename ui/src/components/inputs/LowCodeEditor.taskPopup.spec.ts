import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {defineComponent, h, reactive, ref} from "vue"
import {createPinia, setActivePinia} from "pinia"

const executionsStore = vi.hoisted(() => ({
    execution: {
        id: "exec-1",
        namespace: "company",
        flowId: "flow",
        taskRunList: [{id: "run-1", taskId: "dbt", state: {current: "SUCCESS"}}],
    } as unknown,
    graphExecutions: {},
    progressEvents: [],
    taskRunSelections: new Map(),
    loadAugmentedGraph: vi.fn(),
}))

vi.mock("../../stores/flow", () => ({
    useFlowStore: () => ({flowYaml: "", flowParsed: undefined, flow: undefined, save: vi.fn(), loadGraphFromSource: vi.fn()}),
}))
vi.mock("../../stores/executions", () => ({useExecutionsStore: () => executionsStore}))
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
        componentTypeFor: () => "",
        resolveRemoteComponent: () => Promise.resolve(),
    }),
}))
vi.mock("../executions/TaskRunPopup.vue", () => ({
    default: defineComponent({
        name: "TaskRunPopup",
        props: ["modelValue", "execution", "taskId", "taskType", "description", "nodeActions", "initialTab"],
        render: () => h("div", {"data-test": "task-run-popup"}),
    }),
}))
vi.mock("../executions/TaskRunActions.vue", () => ({
    default: defineComponent({name: "TaskRunActions", render: () => h("div", {"data-test": "task-run-actions"})}),
}))

const nodeActions = [
    {key: "description", label: "Description", onClick: vi.fn()},
    {key: "logs", label: "Logs", onClick: vi.fn()},
    {key: "outputs", label: "Outputs", onClick: vi.fn()},
    {key: "replay", label: "Replay", onClick: vi.fn()},
    {key: "open", label: "Open", onClick: vi.fn()},
]

interface SlotProps {
    variant?: "task"
    task: {id: string, type: string}
    actions: typeof nodeActions
    execution: {id: string}
    taskRuns: {id: string}[]
    taskRun?: {id: string}
}

const slotProps = (overrides: Partial<SlotProps> = {}): SlotProps => ({
    variant: "task",
    task: {id: "dbt", type: "io.kestra.plugin.dbt.cloud.TriggerRun"},
    actions: nodeActions,
    execution: {id: "exec-1"},
    taskRuns: [{id: "run-1"}],
    taskRun: {id: "run-1"},
    ...overrides,
})

const Topology = vi.hoisted(() => ({current: {} as Record<string, unknown>}))
vi.mock("@kestra-io/topology", async (importOriginal) => ({
    ...(await importOriginal<typeof import("@kestra-io/topology")>()),
    Topology: defineComponent({
        name: "Topology",
        props: ["artifactCounts"],
        emits: ["showArtifacts"],
        setup(_, {slots}) {
            return () => h("div", slots.taskActions?.(Topology.current))
        },
    }),
    NodeMenu: defineComponent({name: "NodeMenu", render: () => h("div", {"data-test": "node-menu"})}),
}))

import LowCodeEditor from "./LowCodeEditor.vue"
import {EXECUTION_ARTIFACTS_KEY} from "../executions/composables/useExecutionArtifacts"
import {i18nMount} from "../../../tests/unit/i18nMount"
import en from "../../translations/en.json"

function mountEditor({isReadOnly = true, props = slotProps(), artifacts = []}: {isReadOnly?: boolean, props?: Partial<SlotProps>, artifacts?: {taskId: string}[]} = {}) {
    Topology.current = props
    return i18nMount(LowCodeEditor, {
        locales: en,
        props: {flowGraph: {nodes: [], edges: [], clusters: []}, isReadOnly},
        global: {
            stubs: {teleport: true},
            provide: {
                [EXECUTION_ARTIFACTS_KEY as symbol]: {artifacts: ref(artifacts), manifestReady: ref(true), tasks: ref([]), subtitleResolvers: reactive({})},
            },
        },
    })
}

describe("LowCodeEditor task popup", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
    })

    afterEach(() => {
        localStorage.clear()
    })

    it("shouldOfferAnInfoButtonInsteadOfTheMenuOnAnExecutedTask", async () => {
        // When
        const wrapper = mountEditor()
        await flushPromises()

        // Then
        expect(wrapper.find("[data-test='task-run-popup-trigger']").attributes("aria-label")).toBe("Task details")
        expect(wrapper.find("[data-test='node-menu']").exists()).toBe(false)
        expect(wrapper.find("[data-test='task-run-actions']").exists()).toBe(false)
    })

    it("shouldOpenThePopupOnTheTaskWhenTheInfoButtonIsClicked", async () => {
        // Given
        const wrapper = mountEditor()
        await flushPromises()
        expect(wrapper.findComponent({name: "TaskRunPopup"}).exists()).toBe(false)

        // When
        await wrapper.find("[data-test='task-run-popup-trigger']").trigger("click")

        // Then
        const popup = wrapper.findComponent({name: "TaskRunPopup"})
        expect(popup.props("taskId")).toBe("dbt")
        expect(popup.props("taskType")).toBe("io.kestra.plugin.dbt.cloud.TriggerRun")
        expect(popup.props("initialTab")).toBe("logs")
    })

    it("shouldKeepOnlyTheActionsTheHeaderAndTheTabsDoNotAlreadyCover", async () => {
        // Given
        const wrapper = mountEditor()
        await flushPromises()

        // When
        await wrapper.find("[data-test='task-run-popup-trigger']").trigger("click")

        // Then
        const actions = wrapper.findComponent({name: "TaskRunPopup"}).props("nodeActions") as {key: string, divided?: boolean}[]
        expect(actions.map((action) => action.key)).toEqual(["open"])
        expect(actions[0].divided).toBe(true)
    })

    it("shouldKeepThePopupMountedUntilItsCloseTransitionEnds", async () => {
        // Given
        const wrapper = mountEditor()
        await flushPromises()
        await wrapper.find("[data-test='task-run-popup-trigger']").trigger("click")

        // When
        wrapper.findComponent({name: "TaskRunPopup"}).vm.$emit("update:modelValue", false)
        await flushPromises()

        // Then
        const popup = wrapper.findComponent({name: "TaskRunPopup"})
        expect(popup.exists()).toBe(true)
        expect(popup.props("modelValue")).toBe(false)

        // When
        popup.vm.$emit("closed")
        await flushPromises()

        // Then
        expect(wrapper.findComponent({name: "TaskRunPopup"}).exists()).toBe(false)
    })

    it("shouldOpenThePopupOnTheArtifactsTabFromTheNodeBadge", async () => {
        // Given
        const wrapper = mountEditor()
        await flushPromises()

        // When
        wrapper.findComponent({name: "Topology"}).vm.$emit("showArtifacts", {
            id: "dbt",
            task: {id: "dbt", type: "io.kestra.plugin.dbt.cloud.TriggerRun"},
            execution: {id: "exec-1"},
            taskRuns: [{id: "run-1"}],
            actions: nodeActions,
        })
        await flushPromises()

        // Then
        const popup = wrapper.findComponent({name: "TaskRunPopup"})
        expect(popup.props("taskId")).toBe("dbt")
        expect(popup.props("initialTab")).toBe("artifacts")
    })

    it("shouldKeepTheNodeMenuWhenThereIsNoExecution", async () => {
        // When
        const wrapper = mountEditor({isReadOnly: false})
        await flushPromises()

        // Then
        expect(wrapper.find("[data-test='node-menu']").exists()).toBe(true)
        expect(wrapper.find("[data-test='task-run-popup-trigger']").exists()).toBe(false)
    })

    it("shouldKeepTheNodeMenuOnATaskThatNeverRan", async () => {
        // When
        const wrapper = mountEditor({props: slotProps({taskRuns: [], taskRun: undefined})})
        await flushPromises()

        // Then
        expect(wrapper.find("[data-test='node-menu']").exists()).toBe(true)
        expect(wrapper.find("[data-test='task-run-popup-trigger']").exists()).toBe(false)
    })

    it("shouldKeepTheActionsMenuOnAFlowableLaneHeader", async () => {
        // When
        const wrapper = mountEditor({props: slotProps({variant: undefined})})
        await flushPromises()

        // Then
        expect(wrapper.find("[data-test='task-run-actions']").exists()).toBe(true)
        expect(wrapper.find("[data-test='task-run-popup-trigger']").exists()).toBe(false)
    })

    it("shouldCountTheArtifactsOfEachTaskForTheTopology", async () => {
        // When
        const wrapper = mountEditor({artifacts: [{taskId: "dbt"}, {taskId: "dbt"}, {taskId: "other"}]})
        await flushPromises()

        // Then
        expect(wrapper.findComponent({name: "Topology"}).props("artifactCounts")).toEqual({dbt: 2, other: 1})
    })
})
