import {describe, expect, it} from "vitest"
import {flattenInputs, buildWizardSteps} from "../../../src/utils/inputs"
import {inputsToFormData} from "../../../src/utils/submitTask"

const momentStub = {
    $moment: (_d: any) => ({toISOString: () => "iso", format: (_f: string) => "fmt"}),
}

describe("flattenInputs", () => {
    it("returns [] for undefined", () => {
        expect(flattenInputs(undefined)).toEqual([])
    })

    it("passes non-FORM inputs through unchanged", () => {
        const inputs = [{id: "name", type: "STRING"}, {id: "age", type: "INT"}]
        expect(flattenInputs(inputs)).toEqual(inputs)
    })

    it("expands a FORM into children with dotted ids", () => {
        const inputs = [{
            id: "environment",
            type: "FORM",
            inputs: [{id: "region", type: "STRING"}, {id: "data_center", type: "STRING"}],
        }]
        expect(flattenInputs(inputs)).toEqual([
            {id: "environment.region", type: "STRING"},
            {id: "environment.data_center", type: "STRING"},
        ])
    })

    it("keeps document order across mixed FORM and top-level inputs", () => {
        const inputs = [
            {id: "environment", type: "FORM", inputs: [{id: "region", type: "STRING"}]},
            {id: "api_key", type: "SECRET"},
            {id: "credentials", type: "FORM", inputs: [{id: "token", type: "SECRET"}]},
        ]
        expect(flattenInputs(inputs).map(i => i.id)).toEqual([
            "environment.region",
            "api_key",
            "credentials.token",
        ])
    })

    it("yields nothing for a FORM with no children", () => {
        const inputs = [{id: "empty", type: "FORM", inputs: []}]
        expect(flattenInputs(inputs)).toEqual([])
    })
})

describe("buildWizardSteps", () => {
    it("splits STRING, FORM(STRING), DATE into 3 input steps + recap (the spec example)", () => {
        const steps = buildWizardSteps([
            {id: "name", type: "STRING"},
            {id: "environment", type: "FORM", inputs: [{id: "region", type: "STRING"}]},
            {id: "when", type: "DATE"},
        ])
        expect(steps.map(s => s.kind)).toEqual(["plain", "form", "plain", "recap"])
        expect(steps[0].leafIds).toEqual(["name"])
        expect(steps[1].leafIds).toEqual(["environment.region"]) // dotted form-child id
        expect(steps[1].title).toBe("environment")
        expect(steps[2].leafIds).toEqual(["when"])
    })

    it("collapses a contiguous run of ungrouped inputs into one step", () => {
        const steps = buildWizardSteps([
            {id: "a", type: "STRING"},
            {id: "b", type: "INT"},
            {id: "f", type: "FORM", inputs: [{id: "c", type: "STRING"}]},
            {id: "d", type: "BOOL"},
        ])
        expect(steps.map(s => s.kind)).toEqual(["plain", "form", "plain", "recap"])
        expect(steps[0].leafIds).toEqual(["a", "b"])
        expect(steps[2].leafIds).toEqual(["d"])
    })

    it("titles a FORM step by displayName when present and keeps consecutive forms separate", () => {
        const steps = buildWizardSteps([
            {id: "env", type: "FORM", displayName: "Environment", description: "Pick env", inputs: [{id: "region", type: "STRING"}]},
            {id: "creds", type: "FORM", inputs: [{id: "token", type: "SECRET"}]},
        ])
        expect(steps.map(s => s.kind)).toEqual(["form", "form", "recap"])
        expect(steps[0].title).toBe("Environment")
        expect(steps[0].description).toBe("Pick env")
        expect(steps[1].title).toBe("creds")
        expect(steps[1].leafIds).toEqual(["creds.token"])
    })

    it("skips an empty FORM and still ends with recap", () => {
        const steps = buildWizardSteps([
            {id: "empty", type: "FORM", inputs: []},
            {id: "x", type: "STRING"},
        ])
        expect(steps.map(s => s.kind)).toEqual(["plain", "recap"])
        expect(steps[0].leafIds).toEqual(["x"])
    })

    it("yields a single plain step + recap when there are no FORMs", () => {
        const steps = buildWizardSteps([{id: "a", type: "STRING"}, {id: "b", type: "INT"}])
        expect(steps.map(s => s.kind)).toEqual(["plain", "recap"])
        expect(steps[0].leafIds).toEqual(["a", "b"])
    })
})

describe("inputsToFormData over flattened FORM inputs (submit contract)", () => {
    it("emits dotted part names from a dotted-keyed value map", () => {
        const flowInputs = [{
            id: "environment",
            type: "FORM",
            inputs: [{id: "region", type: "STRING"}],
        }]
        const values = {"environment.region": "EU"}

        const formData = inputsToFormData(momentStub, flattenInputs(flowInputs), values)

        // backend re-nests `environment.region` -> {environment:{region:"EU"}} via flattenToNestedMap
        expect(formData?.get("environment.region")).toBe("EU")
        expect(formData?.get("region")).toBeNull()
        expect(formData?.get("environment")).toBeNull()
    })

    it("drops empty dotted leaves", () => {
        const flowInputs = [{
            id: "environment",
            type: "FORM",
            inputs: [{id: "region", type: "STRING"}, {id: "data_center", type: "STRING"}],
        }]
        const values = {"environment.region": "EU", "environment.data_center": ""}

        const formData = inputsToFormData(momentStub, flattenInputs(flowInputs), values)

        expect(formData?.get("environment.region")).toBe("EU")
        expect(formData?.get("environment.data_center")).toBeNull()
    })
})
