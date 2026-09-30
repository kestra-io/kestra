import {describe, expect, it, vi, beforeEach} from "vitest"
import type {Notification} from "../../../src/stores/notifications"

const loadExecution = vi.fn()

vi.mock("@kestra-io/kestra-sdk/executions", () => ({
    execution: loadExecution,
}))

function buildNotification(overrides: Partial<Omit<Notification, "type">> & {type?: string} = {}): Notification {
    return {
        id: "notif-1",
        userId: "user-1",
        tenantId: "tenant-1",
        type: "GENERIC",
        title: "Something happened",
        referenceId: null,
        current: null,
        total: null,
        read: false,
        createdDate: "2024-01-01T00:00:00Z",
        updatedDate: "2024-01-01T00:00:00Z",
        ...overrides,
    } as Notification
}

describe("resolveNotificationRoute", () => {
    beforeEach(() => {
        vi.resetModules()
        loadExecution.mockReset()
    })

    it("returns null when referenceId is null, regardless of type", async () => {
        const {resolveNotificationRoute} = await import("../../../src/utils/notificationRoute")
        expect(await resolveNotificationRoute(buildNotification({referenceId: null}))).toBeNull()
    })

    it("returns null for a type with no registered route (e.g. GENERIC)", async () => {
        const {resolveNotificationRoute} = await import("../../../src/utils/notificationRoute")
        expect(await resolveNotificationRoute(buildNotification({type: "GENERIC", referenceId: "ref-1"}))).toBeNull()
    })

    it("returns null for a type nobody has registered a route for yet", async () => {
        const {resolveNotificationRoute} = await import("../../../src/utils/notificationRoute")
        expect(await resolveNotificationRoute(buildNotification({type: "SOME_FUTURE_TYPE", referenceId: "ref-1"}))).toBeNull()
    })

    it("looks up the execution to build the executions/update route for HUMAN_TASK_PENDING", async () => {
        loadExecution.mockResolvedValueOnce({id: "exec-1", namespace: "io.kestra.tests", flowId: "test-flow"})
        const {resolveNotificationRoute} = await import("../../../src/utils/notificationRoute")

        const route = await resolveNotificationRoute(buildNotification({type: "HUMAN_TASK_PENDING", referenceId: "exec-1"}))

        expect(loadExecution).toHaveBeenCalledWith({executionId: "exec-1"})
        expect(route).toEqual({name: "executions/update", params: {namespace: "io.kestra.tests", flowId: "test-flow", id: "exec-1"}})
    })

    it("returns null for HUMAN_TASK_PENDING when the execution lookup fails", async () => {
        loadExecution.mockRejectedValueOnce(new Error("not found"))
        const {resolveNotificationRoute} = await import("../../../src/utils/notificationRoute")

        expect(await resolveNotificationRoute(buildNotification({type: "HUMAN_TASK_PENDING", referenceId: "exec-1"}))).toBeNull()
    })

    it("looks up the execution to build the executions/update route for SYSTEM_EXECUTION_FAILED", async () => {
        loadExecution.mockResolvedValueOnce({id: "exec-2", namespace: "system", flowId: "housekeeping"})
        const {resolveNotificationRoute} = await import("../../../src/utils/notificationRoute")

        const route = await resolveNotificationRoute(buildNotification({type: "SYSTEM_EXECUTION_FAILED", referenceId: "exec-2"}))

        expect(loadExecution).toHaveBeenCalledWith({executionId: "exec-2"})
        expect(route).toEqual({name: "executions/update", params: {namespace: "system", flowId: "housekeeping", id: "exec-2"}})
    })

    it("returns null for SYSTEM_EXECUTION_FAILED when the execution lookup fails", async () => {
        loadExecution.mockRejectedValueOnce(new Error("not found"))
        const {resolveNotificationRoute} = await import("../../../src/utils/notificationRoute")

        expect(await resolveNotificationRoute(buildNotification({type: "SYSTEM_EXECUTION_FAILED", referenceId: "exec-2"}))).toBeNull()
    })

    it("lets an outside producer (e.g. an EE feature) register its own route", async () => {
        const {resolveNotificationRoute, registerNotificationRoute} = await import("../../../src/utils/notificationRoute")
        registerNotificationRoute("SOME_PRODUCER_TYPE", (notification) => ({name: "some/route", params: {id: notification.referenceId}}))

        expect(await resolveNotificationRoute(buildNotification({type: "SOME_PRODUCER_TYPE", referenceId: "id-1"})))
            .toEqual({name: "some/route", params: {id: "id-1"}})
    })
})

describe("isNotificationLinkable", () => {
    it("returns true only for types with a registered route", async () => {
        const {isNotificationLinkable} = await import("../../../src/utils/notificationRoute")

        expect(isNotificationLinkable("HUMAN_TASK_PENDING")).toBe(true)
        expect(isNotificationLinkable("SYSTEM_EXECUTION_FAILED")).toBe(true)
        expect(isNotificationLinkable("GENERIC")).toBe(false)
        expect(isNotificationLinkable("SOME_FUTURE_TYPE")).toBe(false)
    })
})
