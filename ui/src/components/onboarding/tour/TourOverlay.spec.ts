import {afterAll, afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, type VueWrapper} from "@vue/test-utils"
import {createMemoryHistory, createRouter, type Router} from "vue-router"
import {createPinia, setActivePinia} from "pinia"
import {defineComponent, h} from "vue"

import {i18nMount} from "../../../../tests/unit/i18nMount"
import {TOUR_FLOW_ID, TOUR_NAMESPACE} from "./tourFlows"
import {TOUR_SCENES} from "./tourScenes"

const mocks = vi.hoisted(() => ({
    actions: {} as Record<string, ReturnType<typeof vi.fn>>,
    trackOnboarding: vi.fn(),
}))

vi.mock("override/components/onboarding/tour/useTourVariant", async () => {
    const {TOUR_SCENES: scenes} = await import("./tourScenes")
    const {defineComponent: define, h: render} = await import("vue")
    return {
        useTourVariant: () => ({
            id: "product_tour",
            i18nPrefix: "onboarding.tour",
            scenes,
            finale: define({setup: () => () => render("div")}),
            autoStartRoute: "ai",
            useActions: () => mocks.actions,
        }),
    }
})
vi.mock("override/stores/misc", () => ({useMiscStore: () => ({configs: {}})}))
vi.mock("../../../composables/useOnboardingAnalytics", () => ({
    useOnboardingAnalytics: () => ({trackOnboarding: mocks.trackOnboarding}),
}))
vi.mock("../../../utils/toast", () => ({useToast: () => ({success: vi.fn()})}))

import TourOverlay from "./TourOverlay.vue"
import {useProductTourStore} from "../../../stores/productTour"

const Page = defineComponent({setup: () => () => h("div")})

const executionPath = (id: string, tab: string) => `/executions/${TOUR_NAMESPACE}/${TOUR_FLOW_ID}/${id}/${tab}`

const createTestRouter = () => createRouter({
    history: createMemoryHistory(),
    routes: [
        {path: "/", name: "home", component: Page},
        {path: "/executions/:namespace/:flowId/:id/gantt", name: "executions/update/gantt", component: Page},
        {path: "/executions/:namespace/:flowId/:id/logs", name: "executions/update/logs", component: Page},
        {path: "/executions/:namespace/:flowId/:id/outputs", name: "executions/update/outputs", component: Page},
    ],
})

const execution = (id: string, state: string) => ({id, state: {current: state}})

const installLocalStorage = () => {
    const entries = new Map<string, string>()
    vi.stubGlobal("localStorage", {
        getItem: (key: string) => entries.get(key) ?? null,
        setItem: (key: string, value: string) => void entries.set(key, value),
        removeItem: (key: string) => void entries.delete(key),
        clear: () => entries.clear(),
    })
}

describe("TourOverlay", () => {
    let router: Router
    let wrapper: VueWrapper | undefined

    beforeEach(async () => {
        installLocalStorage()
        vi.stubGlobal("matchMedia", () => ({matches: true}))
        setActivePinia(createPinia())

        Object.assign(mocks.actions, {
            tourFlowExists: vi.fn().mockResolvedValue(false),
            removeReportFlow: vi.fn().mockResolvedValue(undefined),
            applySource: vi.fn().mockResolvedValue(undefined),
            executeFlow: vi.fn(),
            waitForExecution: vi.fn(),
            dockerUnavailable: vi.fn().mockResolvedValue(false),
            openExecution: vi.fn().mockResolvedValue(undefined),
            restoreEditorPanels: vi.fn(),
        })
        mocks.trackOnboarding.mockReset()

        router = createTestRouter()
        await router.push("/")
        await router.isReady()
    })

    afterEach(() => {
        wrapper?.unmount()
        wrapper = undefined
    })

    afterAll(() => {
        vi.unstubAllGlobals()
    })

    const mountOnFlowGenerated = async () => {
        const store = useProductTourStore()
        store.startGuided({id: "product_tour", scenes: TOUR_SCENES})
        store.setTourState({introSeen: true})
        store.setStep("flow_generated")

        wrapper = i18nMount(TourOverlay, {global: {plugins: [router]}, attachTo: document.body})
        await flushPromises()
        return store
    }

    const find = (selector: string) => document.body.querySelector<HTMLElement>(selector)
    const click = async (selector: string) => {
        find(selector)?.click()
        await flushPromises()
    }
    const goTo = async (path: string) => {
        await router.push(path)
        await flushPromises()
    }

    it("does not advance when the user opens the tabs of a failed execution", async () => {
        mocks.actions.waitForExecution.mockResolvedValue(execution("exec-1", "FAILED"))
        const store = await mountOnFlowGenerated()

        await goTo(executionPath("exec-1", "gantt"))
        await goTo(executionPath("exec-1", "logs"))
        await goTo(executionPath("exec-1", "outputs"))

        expect(store.state.currentStepId).toBe("flow_generated")
        expect(mocks.actions.waitForExecution).toHaveBeenCalledTimes(1)
        expect(find("[data-test=tour-error-recovery]")).toBeNull()
        expect(document.body.textContent).toContain("onboarding.tour.errors.first_execution")
    })

    it("advances when the execution the user started succeeds", async () => {
        mocks.actions.waitForExecution.mockResolvedValue(execution("exec-1", "SUCCESS"))
        const store = await mountOnFlowGenerated()

        await goTo(executionPath("exec-1", "gantt"))

        expect(store.state.currentStepId).toBe("first_execution")
        expect(store.state.tour.lastExecutionId).toBe("exec-1")
    })

    it("verifies a new execution after a failed one", async () => {
        mocks.actions.waitForExecution
            .mockResolvedValueOnce(execution("exec-1", "FAILED"))
            .mockResolvedValueOnce(execution("exec-2", "SUCCESS"))
        const store = await mountOnFlowGenerated()

        await goTo(executionPath("exec-1", "gantt"))
        await goTo(executionPath("exec-2", "gantt"))

        expect(store.state.currentStepId).toBe("first_execution")
    })

    it("offers to run without Docker, then runs the example on the Process runner", async () => {
        mocks.actions.dockerUnavailable.mockResolvedValue(true)
        mocks.actions.executeFlow
            .mockResolvedValueOnce(execution("exec-1", "FAILED"))
            .mockResolvedValueOnce(execution("exec-2", "SUCCESS"))
        const store = await mountOnFlowGenerated()

        await click("[data-test=tour-next]")

        expect(document.body.textContent).toContain("onboarding.tour.errors.docker_unavailable")
        expect(find("[data-test=tour-error-help]")?.getAttribute("href"))
            .toContain("https://kestra.io/docs/installation/docker")
        expect(mocks.actions.applySource.mock.calls[0][0]).not.toContain("taskRunner")

        await click("[data-test=tour-error-recovery]")

        expect(store.state.tour.taskRunner).toBe("process")
        expect(mocks.actions.applySource.mock.calls[1][0]).toContain("type: io.kestra.plugin.core.runner.Process")
        expect(store.state.currentStepId).toBe("first_execution")
        expect(mocks.trackOnboarding).toHaveBeenCalledWith(expect.objectContaining({event: "tour_docker_unavailable"}))
        expect(mocks.trackOnboarding).toHaveBeenCalledWith(expect.objectContaining({event: "tour_docker_fallback"}))
    })

    it("says the execution is still running, blocks a second run, and stops waiting on skip", async () => {
        let signal: AbortSignal | undefined
        mocks.actions.executeFlow.mockImplementation((_flowId: string, options: {signal: AbortSignal; onStillRunning: () => void}) => {
            signal = options.signal
            options.onStillRunning()
            return new Promise((_resolve, reject) => {
                options.signal.addEventListener("abort", () => reject(new DOMException("aborted", "AbortError")))
            })
        })
        const store = await mountOnFlowGenerated()

        await click("[data-test=tour-next]")

        expect(find("[data-test=tour-notice]")?.textContent).toContain("onboarding.tour.notices.still_running")
        expect(find("[data-test=tour-next]")?.hasAttribute("disabled")).toBe(true)
        expect(mocks.actions.executeFlow).toHaveBeenCalledTimes(1)

        await click("[data-test=tour-skip]")

        expect(signal?.aborted).toBe(true)
        expect(store.state.status).toBe("skipped")
        expect(find("[data-test=tour-notice]")).toBeNull()
    })
})
