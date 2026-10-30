import {beforeEach, describe, expect, it, vi} from "vitest"
import {defineComponent, h, nextTick, ref} from "vue"
import {mount, flushPromises} from "@vue/test-utils"

const isReviewAllowed = vi.hoisted(() => vi.fn())
const permissions = vi.hoisted(() => ({review: true}))

vi.mock("../stores/executions", () => ({
    useExecutionsStore: () => ({
        flow: {tasks: [{id: "approval", type: "io.kestra.plugin.core.flow.Approval"}]},
        isReviewAllowed,
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
        taskRunList: [
            {id: "tr1", taskId: "approval", state: {current: "PAUSED"}},
            {id: "tr2", taskId: "approval", state: {current: "PAUSED"}},
        ],
    } as unknown as Execution
}

function mountReviewer(execution: Execution | (() => Execution), taskRunId?: () => string | undefined) {
    const current = typeof execution === "function" ? execution : () => execution
    let enabled: {value: boolean} | undefined
    mount(defineComponent({
        setup() {
            enabled = useApprovalReviewer(current, taskRunId).enabled
            return () => h("div")
        },
    }))
    return () => enabled?.value
}

describe("useApprovalReviewer", () => {
    beforeEach(() => {
        isReviewAllowed.mockReset()
        permissions.review = true
    })

    it("is enabled when the server says the viewer may review", async () => {
        isReviewAllowed.mockResolvedValue(true)
        const enabled = mountReviewer(pausedExecution())
        await flushPromises()
        await nextTick()

        expect(isReviewAllowed).toHaveBeenCalledWith({id: "e1", taskRunId: "tr1"})
        expect(enabled()).toBe(true)
    })

    it("stays disabled when the viewer is not assigned", async () => {
        isReviewAllowed.mockResolvedValue(false)
        const enabled = mountReviewer(pausedExecution())
        await flushPromises()

        expect(enabled()).toBe(false)
    })

    it("does not ask the server without the review permission", async () => {
        permissions.review = false
        isReviewAllowed.mockResolvedValue(true)
        const enabled = mountReviewer(pausedExecution())
        await flushPromises()

        expect(isReviewAllowed).not.toHaveBeenCalled()
        expect(enabled()).toBe(false)
    })

    it("asks about the requested approval rather than the first paused one", async () => {
        isReviewAllowed.mockImplementation(({taskRunId}: {taskRunId: string}) => Promise.resolve(taskRunId === "tr2"))
        const enabled = mountReviewer(pausedExecution(), () => "tr2")
        await flushPromises()

        expect(isReviewAllowed).toHaveBeenCalledWith({id: "e1", taskRunId: "tr2"})
        expect(enabled()).toBe(true)
    })

    it("ignores a late answer for an approval that is no longer the paused one", async () => {
        let allowFirst: (allowed: boolean) => void = () => {}
        isReviewAllowed.mockImplementation(({taskRunId}: {taskRunId: string}) =>
            taskRunId === "tr1" ? new Promise<boolean>(resolve => allowFirst = resolve) : Promise.resolve(false),
        )
        const execution = ref(pausedExecution())
        const enabled = mountReviewer(() => execution.value)
        await flushPromises()

        execution.value = {
            ...execution.value,
            taskRunList: [
                {id: "tr1", taskId: "approval", state: {current: "SUCCESS"}},
                {id: "tr2", taskId: "approval", state: {current: "PAUSED"}},
            ],
        } as unknown as Execution
        await flushPromises()
        allowFirst(true)
        await flushPromises()

        expect(isReviewAllowed).toHaveBeenCalledWith({id: "e1", taskRunId: "tr2"})
        expect(enabled()).toBe(false)
    })
})
