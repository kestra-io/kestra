import {readFileSync} from "node:fs"
import path from "node:path"
import {describe, expect, it} from "vitest"
import {EXECUTION_STATUSES} from "@kestra-io/design-system"
import type {ServiceServiceState} from "@kestra-io/kestra-sdk"
import {SERVICE_STATE_TO_EXECUTION_STATUS} from "../../../src/utils/serviceState"

const SDK_TYPES = path.resolve(__dirname, "../../../packages/kestra-sdk/src/openapi/types.gen.ts")

const serviceStatesFromSdk = (): ServiceServiceState[] => {
    const source = readFileSync(SDK_TYPES, "utf-8")
    const union = source.match(/export type ServiceServiceState = ([^;]+);/)?.[1]

    if (union === undefined) {
        throw new Error("ServiceServiceState was not found in the generated SDK types.")
    }

    return [...union.matchAll(/'([^']+)'/g)].map(match => match[1] as ServiceServiceState)
}

describe("SERVICE_STATE_TO_EXECUTION_STATUS", () => {
    it("maps every service state to a valid execution status", () => {
        const invalidStatuses = Object.values(SERVICE_STATE_TO_EXECUTION_STATUS)
            .filter(status => !Object.prototype.hasOwnProperty.call(EXECUTION_STATUSES, status))

        expect(invalidStatuses).toEqual([])
    })

    it("covers every service state from the SDK", () => {
        expect(Object.keys(SERVICE_STATE_TO_EXECUTION_STATUS).sort())
            .toEqual(serviceStatesFromSdk().sort())
    })

    it("keeps the service states with non-obvious status mappings pinned", () => {
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
