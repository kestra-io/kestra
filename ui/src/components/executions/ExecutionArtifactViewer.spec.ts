import {afterAll, describe, expect, it, vi} from "vitest"
import {defineComponent, h, reactive, ref} from "vue"
import {designSystemI18nReady} from "@kestra-io/design-system"

vi.mock("../../stores/plugins", () => ({usePluginsStore: () => ({icons: {}, loadIcon: vi.fn(), fetchIcons: vi.fn()})}))
vi.mock("../plugins/TaskIcon.vue", () => ({default: defineComponent({render: () => null})}))

import ExecutionArtifactViewer from "./ExecutionArtifactViewer.vue"
import {EXECUTION_ARTIFACTS_KEY} from "./composables/useExecutionArtifacts"
import type {Execution} from "../../stores/executions"
import {i18nMount} from "../../../tests/unit/i18nMount"
import {routedPinia} from "../../../tests/unit/memoryRouter"
import {setActivePinia} from "pinia"

const artifact = (key: string, title: string) => ({key, taskId: "dbt", taskType: key, cls: "dbt.Cli", title})
const GRAPH = artifact("dbt:graph", "Graph")
const DOCS = artifact("dbt:docs", "Docs")

const RemoteComponent = defineComponent({name: "RemoteComponent", render: () => h("div", {"data-test": "remote"})})

function mountViewer({siblings = [GRAPH], fullscreen, resolved = true, failed = false}: {siblings?: typeof GRAPH[], fullscreen?: boolean, resolved?: boolean, failed?: boolean} = {}) {
    setActivePinia(routedPinia())
    return i18nMount(ExecutionArtifactViewer, {
        messages: {execution_artifacts: {fullscreen: "Fullscreen", exit_fullscreen: "Exit fullscreen"}, plugins: {ui_load_error: "Load error"}},
        props: {artifact: GRAPH, siblings, execution: {id: "e", namespace: "ns", flowId: "f"} as Execution, ...(fullscreen === undefined ? {} : {fullscreen})},
        global: {
            stubs: {RemoteComponent: false},
            provide: {
                [EXECUTION_ARTIFACTS_KEY as symbol]: {
                    RemoteComponent,
                    tasks: ref([]),
                    subtitleResolvers: reactive({}),
                    manifestReady: ref(true),
                    artifacts: ref([]),
                    hasResolvedComponent: () => resolved,
                    hasFailedComponent: () => failed,
                },
            },
        },
    })
}

afterAll(() => designSystemI18nReady())

describe("ExecutionArtifactViewer", () => {
    it("shouldShowNoFullscreenButtonWhenTheHostDoesNotDriveFullscreen", () => {
        expect(mountViewer().find("[data-test='execution-artifact-fullscreen']").exists()).toBe(false)
    })

    it("shouldEmitToggleFullscreenWhenTheHostDrivesFullscreen", async () => {
        const wrapper = mountViewer({fullscreen: false})

        await wrapper.get("[data-test='execution-artifact-fullscreen']").trigger("click")

        expect(wrapper.emitted("toggleFullscreen")).toHaveLength(1)
    })

    it("shouldOnlyOfferTheSiblingSwitcherWhenTheTaskHasSeveralArtifacts", () => {
        expect(mountViewer().findComponent({name: "KsSegmented"}).exists()).toBe(false)
        expect(mountViewer({siblings: [GRAPH, DOCS]}).findComponent({name: "KsSegmented"}).exists()).toBe(true)
    })

    it("shouldRenderTheRemoteComponentOnceItIsResolved", () => {
        expect(mountViewer().find("[data-test='remote']").exists()).toBe(true)
    })

    it("shouldShowAnErrorWhenTheRemoteComponentFailedToLoad", () => {
        const wrapper = mountViewer({resolved: false, failed: true})

        expect(wrapper.find("[data-test='execution-artifact-error']").exists()).toBe(true)
        expect(wrapper.find("[data-test='remote']").exists()).toBe(false)
    })

    it("shouldShowASkeletonWhileTheRemoteComponentLoads", () => {
        expect(mountViewer({resolved: false}).find("[data-test='execution-artifact-loading']").exists()).toBe(true)
    })
})
