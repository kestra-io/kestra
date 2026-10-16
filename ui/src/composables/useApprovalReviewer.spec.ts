import {beforeEach, describe, expect, it, vi} from "vitest"
import {defineComponent, h, nextTick} from "vue"
import {mount, flushPromises} from "@vue/test-utils"

const reviewAllowed = vi.hoisted(() => vi.fn())
const permissions = vi.hoisted(() => ({review: true}))

vi.mock("../stores/executions", () => ({
    useExecutionsStore: () => ({
        flow: {tasks: [{id: "approval", type: "io.kestra.plugin.core.flow.Approval"}]},
        reviewAllowed,
    }),
}))

vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({
        user: {isAllowed: () => permissions.review},
    }),
}))

import {useApprovalReviewer} from "./useApprovalReviewer"
import type {Execution} from "../stores/executions"

function pausedExecution(): Execution {
    return {
        id: "e1",
        namespace: "ns",
        taskRunList: [{id: "tr1", taskId: "approval", state: {current: "PAUSED"}}],
    } as unknown as Execution
}

function mountReviewer(execution: Execution) {
    let enabled: {value: boolean} | undefined
    mount(defineComponent({
        setup() {
            enabled = useApprovalReviewer(() => execution).enabled
            return () => h("div")
        },
    }))
    return () => enabled?.value
}

describe("useApprovalReviewer", () => {
    beforeEach(() => {
        reviewAllowed.mockReset()
        permissions.review = true
    })

    it("is enabled when the server says the viewer may review", async () => {
        reviewAllowed.mockResolvedValue(true)
        const enabled = mountReviewer(pausedExecution())
        await flushPromises()
        await nextTick()

        expect(reviewAllowed).toHaveBeenCalledWith({id: "e1", taskRunId: "tr1"})
        expect(enabled()).toBe(true)
    })

    it("stays disabled when the viewer is not assigned", async () => {
        reviewAllowed.mockResolvedValue(false)
        const enabled = mountReviewer(pausedExecution())
        await flushPromises()

        expect(enabled()).toBe(false)
    })

    it("stays disabled when the check fails", async () => {
        reviewAllowed.mockRejectedValue(new Error("403"))
        const enabled = mountReviewer(pausedExecution())
        await flushPromises()

        expect(enabled()).toBe(false)
    })

    it("stays disabled without the review permission even when the server allows it", async () => {
        permissions.review = false
        reviewAllowed.mockResolvedValue(true)
        const enabled = mountReviewer(pausedExecution())
        await flushPromises()

        expect(enabled()).toBe(false)
    })
})
