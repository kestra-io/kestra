import {readFileSync} from "node:fs"
import path from "node:path"
import {describe, expect, it} from "vitest"
import {EXECUTION_STATUSES} from "@kestra-io/design-system"
import type {ServiceServiceState} from "@kestra-io/kestra-sdk"
import {SERVICE_STATE_TO_EXECUTION_STATUS} from "../../../src/utils/serviceState"

const SDK_TYPES_PATH = path.resolve(__dirname, "../../../packages/kestra-sdk/src/openapi/types.gen.ts")

const extractServiceStatesFromSdk = (): ServiceServiceState[] => {
    const fileContent = readFileSync(SDK_TYPES_PATH, "utf-8")
    const match = fileContent.match(/export type ServiceServiceState = ([^;]+);/)
    if (!match?.[1]) {
        throw new Error("ServiceServiceState definition not found in generated SDK types")
    }

    return [...match[1].matchAll(/'([^']+)'/g)].map(m => m[1] as ServiceServiceState)
}

describe("SERVICE_STATE_TO_EXECUTION_STATUS", () => {
    it("maps every service state to a valid execution status", () => {
        const statuses = Object.values(SERVICE_STATE_TO_EXECUTION_STATUS)
        for (const status of statuses) {
            expect(Object.keys(EXECUTION_STATUSES)).toContain(status)
        }
    })

    it("exhaustively covers every ServiceServiceState defined in the SDK", () => {
        const expectedStates = extractServiceStatesFromSdk()
        const mappedStates = Object.keys(SERVICE_STATE_TO_EXECUTION_STATUS)

        expect(mappedStates.sort()).toEqual(expectedStates.sort())
    })

    it("pins the non-obvious pairings required by the application", () => {
        expect(SERVICE_STATE_TO_EXECUTION_STATUS).toMatchObject({
            DISCONNECTED: "FAILED",
            TERMINATED_FORCED: "FAILED",
            MAINTENANCE: "WARNING",
            TERMINATING: "WARNING",
            TERMINATED_GRACEFULLY: "PAUSED",
            NOT_RUNNING: "PAUSED",
        })
    })
})
