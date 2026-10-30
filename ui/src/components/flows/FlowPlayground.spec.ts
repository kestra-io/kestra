import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {reactive} from "vue"
import {i18nShallowMount} from "../../../tests/unit/i18nMount"

const playgroundStore = reactive({
    latestExecution: undefined as Record<string, unknown> | undefined,
    executions: [] as unknown[],
    showInputPrompt: false,
    loadSession: vi.fn(() => Promise.resolve()),
    runFromQuery: vi.fn(),
})
const executionsStore = reactive({
    execution: undefined,
    followExecution: vi.fn(),
    closeSSE: vi.fn(),
})

vi.mock("../../stores/playground", () => ({usePlaygroundStore: () => playgroundStore}))
vi.mock("../../stores/executions", () => ({useExecutionsStore: () => executionsStore}))

import FlowPlayground from "./FlowPlayground.vue"

describe("FlowPlayground", () => {
    beforeEach(() => {
        vi.clearAllMocks()
        playgroundStore.latestExecution = undefined
    })

    afterEach(() => {
        localStorage.clear()
    })

    it("follows the run it already knows when the panel is reopened, since the server may have ended it", async () => {
        playgroundStore.latestExecution = {id: "e1", state: {current: "BREAKPOINT"}}

        i18nShallowMount(FlowPlayground)
        await flushPromises()

        expect(executionsStore.followExecution).toHaveBeenCalledWith(expect.objectContaining({id: "e1"}), expect.any(Function))
    })

    it("follows the latest run the session loads", async () => {
        playgroundStore.loadSession.mockImplementationOnce(async () => {
            playgroundStore.latestExecution = {id: "e2", state: {current: "BREAKPOINT"}}
        })

        i18nShallowMount(FlowPlayground)
        await flushPromises()

        expect(executionsStore.followExecution).toHaveBeenCalledWith(expect.objectContaining({id: "e2"}), expect.any(Function))
    })
})
