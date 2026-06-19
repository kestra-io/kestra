import {describe, expect, it} from "vitest"
import {FlowAutoCompletion} from "../../../src/override/services/flowAutoCompletionProvider"

// The `inputs` / `inputs.<form>` branches of nestedFieldAutoCompletion are pure functions of
// `parsed` and never touch the injected stores, so stub stores are enough.
function provider() {
    return new FlowAutoCompletion(
        {} as any,
        {} as any,
        {} as any,
        {} as any,
    )
}

const parsed = {
    inputs: [
        {
            id: "environment",
            type: "FORM",
            inputs: [{id: "region", type: "SELECT"}, {id: "data_center", type: "STRING"}],
        },
        {id: "api_key", type: "STRING"},
    ],
}

describe("FlowAutoCompletion inputs autocomplete with FORM groups", () => {
    it("level 1 (`inputs.`) lists top-level ids: form id + ungrouped id", async () => {
        const result = await provider().nestedFieldAutoCompletion("", parsed, "inputs")
        expect(result).toEqual(["environment", "api_key"])
    })

    it("level 2 (`inputs.environment.`) lists the FORM's children", async () => {
        const result = await provider().nestedFieldAutoCompletion("", parsed, "inputs.environment")
        expect(result).toEqual(["region", "data_center"])
    })

    it("returns [] for a non-FORM top-level input", async () => {
        const result = await provider().nestedFieldAutoCompletion("", parsed, "inputs.api_key")
        expect(result).toEqual([])
    })

    it("returns [] for an unknown form id", async () => {
        const result = await provider().nestedFieldAutoCompletion("", parsed, "inputs.unknown")
        expect(result).toEqual([])
    })
})
