import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import Overview from "./Overview.vue"
import {i18nMount} from "../../../../tests/unit/i18nMount"
import {useExecutionsStore} from "../../../stores/executions"

vi.mock("../../../composables/useIsMobile", () => ({
    useIsMobile: () => false,
}))

vi.mock("../../../composables/useFirstFailure", () => ({
    useFirstFailure: () => ({failedTaskRun: {value: undefined}}),
}))

function buildExecution(current = "RUNNING") {
    return {
        id: "exec-1",
        namespace: "company.team",
        flowId: "my-flow",
        state: {
            current,
            startDate: new Date().toISOString(),
            histories: [],
        },
    }
}

describe("Overview.vue", () => {
    let store: ReturnType<typeof useExecutionsStore>

    afterEach(() => {
        localStorage.clear()
    })

    beforeEach(() => {
        localStorage.clear()
        setActivePinia(createPinia())
        store = useExecutionsStore()
    })

    it("renders ExecutionProgress when execution is running", () => {
        store.execution = buildExecution("RUNNING") as never

        const wrapper = i18nMount(Overview, {
            global: {
                stubs: {
                    Banner: true,
                    Topology: true,
                    PrevNext: true,
                    ErrorAlert: true,
                    PausedAlert: true,
                    ExecutionProgress: {
                        props: ["execution"],
                        template: "<div data-test=\"overview-progress\" :data-status=\"execution.state.current\" />",
                    },
                },
            },
        })

        const progress = wrapper.find("[data-test=\"overview-progress\"]")
        expect(progress.exists()).toBe(true)
        expect(progress.attributes("data-status")).toBe("RUNNING")
    })

    it("does not render ExecutionProgress when execution is not running", () => {
        store.execution = buildExecution("SUCCESS") as never

        const wrapper = i18nMount(Overview, {
            global: {
                stubs: {
                    Banner: true,
                    Topology: true,
                    PrevNext: true,
                    ErrorAlert: true,
                    PausedAlert: true,
                    ExecutionProgress: {
                        props: ["execution"],
                        template: "<div data-test=\"overview-progress\" />",
                    },
                },
            },
        })

        expect(wrapper.find("[data-test=\"overview-progress\"]").exists()).toBe(false)
    })
})
