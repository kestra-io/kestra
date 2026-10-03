import {describe, it, expect, beforeEach, vi} from "vitest"
import {ref, nextTick} from "vue"

const get = vi.fn()

vi.mock("@kestra-io/kestra-sdk", () => ({useClient: () => ({get})}))
vi.mock("override/utils/route", () => ({apiUrl: () => "/api/v1/main"}))

import {useAiUsage, type AiUsageStatus} from "../../../src/components/ai/copilot/useAiUsage"

const status = (overrides: Partial<AiUsageStatus> = {}): AiUsageStatus => ({
    providerId: "gemini-1",
    enabled: true,
    windowStart: "2026-01-01T00:00:00Z",
    global: {weight: 900, maxWeight: 1_000, remainingPercent: 10, exceeded: false},
    user: null,
    remainingPercent: 10,
    warning: true,
    exceeded: false,
    warningThresholdPercent: 10,
    ...overrides,
})

const flush = async () => {
    await nextTick()
    await Promise.resolve()
    await Promise.resolve()
}

beforeEach(() => {
    get.mockReset()
    get.mockResolvedValue({data: status()})
})

describe("useAiUsage", () => {
    it("reads the figure and both flags from the server rather than deriving them", async () => {
        const usage = useAiUsage(ref("gemini-1"))
        await flush()

        // The percentage and the warning flag are the server's: recomputing either here is how a figure shown
        // stops matching the one being enforced mid-turn.
        expect(usage.remainingPercent.value).toBe(10)
        expect(usage.warning.value).toBe(true)
        expect(usage.exceeded.value).toBe(false)
        expect(get).toHaveBeenCalledWith("/api/v1/main/ai/usage", expect.objectContaining({
            params: {providerId: "gemini-1"},
            showMessageOnError: false,
        }))
    })

    it("shows nothing for a provider whose limits are switched off", async () => {
        get.mockResolvedValue({data: status({enabled: false, global: null, warning: true, exceeded: true})})
        const usage = useAiUsage(ref("gemini-1"))
        await flush()

        // A disabled limit reports neither a warning nor an exhausted state, whatever else the payload carries:
        // usage is recorded for every provider, and only a configured ceiling is shown.
        expect(usage.shown.value).toBe(false)
        expect(usage.warning.value).toBe(false)
        expect(usage.exceeded.value).toBe(false)
    })

    it("re-reads when the provider changes, since each carries its own ceiling", async () => {
        const provider = ref<string | undefined>("gemini-1")
        useAiUsage(provider)
        await flush()

        provider.value = "openai-1"
        await flush()

        expect(get).toHaveBeenCalledTimes(2)
        expect(get.mock.calls[1][1]).toMatchObject({params: {providerId: "openai-1"}})
    })

    it("keeps the last known figure when the endpoint cannot be read", async () => {
        const usage = useAiUsage(ref("gemini-1"))
        await flush()

        get.mockRejectedValue(new Error("gateway is down"))
        await expect(usage.refresh()).resolves.toBeUndefined()

        // Neither cleared nor thrown: the ceiling is enforced server-side regardless, and emptying the figure
        // over a transient failure would read as "no limit" on a provider that has one.
        expect(usage.remainingPercent.value).toBe(10)
        expect(usage.shown.value).toBe(true)
    })
})
