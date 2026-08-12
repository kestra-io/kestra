import {describe, expect, it} from "vitest"
import {flattenInputs, unflattenToForms, formChildName, buildWizardSteps, normalize} from "../../../src/utils/inputs"
import {inputsToFormData} from "../../../src/utils/submitTask"

const momentStub = {
    $moment: (_d: any) => ({toISOString: () => "iso", format: (_f: string) => "fmt"}),
}

// Regression guard, fixed more than once: `defaults` is a Property, so it crosses the wire as its
// expression STRING — a `defaults: true` BOOL arrives as "true". el-switch only accepts a real
// boolean; anything else makes it emit `update:modelValue` = false during setup, which turns the
// toggle off AND marks the input as user-edited so the default can never come back.
// See https://github.com/kestra-io/kestra-ee/issues/9772 (and /8978 before it).
describe("normalize for BOOL always yields a real boolean", () => {
    it("coerces the string form of a default", () => {
        expect(normalize("BOOL", "true")).toBe(true)
        expect(normalize("BOOL", "false")).toBe(false)
    })

    it("passes real booleans through", () => {
        expect(normalize("BOOL", true)).toBe(true)
        expect(normalize("BOOL", false)).toBe(false)
    })

    it("falls back to false when there is no value at all", () => {
        expect(normalize("BOOL", undefined)).toBe(false)
        expect(normalize("BOOL", null)).toBe(false)
    })

    it("never yields a non-boolean, whatever the input", () => {
        for (const value of ["true", "false", true, false, undefined, null, "", "TRUE", 1, 0, {}]) {
            expect(typeof normalize("BOOL", value)).toBe("boolean")
        }
    })

    // BOOLEAN is the retired input type; it uses a radio group with an "undefined" third state,
    // so it must NOT be swept into the boolean coercion.
    it("leaves the retired BOOLEAN type's tri-state alone", () => {
        expect(normalize("BOOLEAN", undefined)).toBe("undefined")
        expect(normalize("BOOLEAN", "true")).toBe("true")
    })
})

describe("normalize for ION uses the structured-data editor contract", () => {
    it("serializes structured values", () => {
        expect(normalize("ION", {name: "Ada"})).toBe('{"name":"Ada"}')
    })

    it("preserves Ion text", () => {
        expect(normalize("ION", '{name:"Ada"}')).toBe('{name:"Ada"}')
    })
})

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

describe("unflattenToForms", () => {
    it("returns [] for undefined leaves", () => {
        expect(unflattenToForms(undefined, {})).toEqual([])
    })

    it("passes leaves through unchanged when there are no form groups", () => {
        const leaves = [{id: "name", type: "STRING"}, {id: "age", type: "INT"}]
        expect(unflattenToForms(leaves, undefined)).toEqual(leaves)
        expect(unflattenToForms(leaves, {})).toEqual(leaves)
    })

    it("rebuilds a FORM node (displayName/description from formGroups) with bare-id children", () => {
        const leaves = [
            {id: "environment.region", type: "STRING", displayName: "Region"},
            {id: "environment.zone", type: "STRING"},
            {id: "api_key", type: "SECRET", displayName: "API Key"},
        ]
        const groups = {environment: {displayName: "Environment", description: "Pick env"}}
        expect(unflattenToForms(leaves, groups)).toEqual([
            {
                id: "environment",
                type: "FORM",
                displayName: "Environment",
                description: "Pick env",
                inputs: [
                    {id: "region", type: "STRING", displayName: "Region"},
                    {id: "zone", type: "STRING"},
                ],
            },
            {id: "api_key", type: "SECRET", displayName: "API Key"},
        ])
    })

    it("round-trips with flattenInputs (the inverse invariant)", () => {
        const leaves = [
            {id: "environment.region", type: "STRING", displayName: "Region"},
            {id: "environment.zone", type: "STRING"},
            {id: "api_key", type: "SECRET", displayName: "API Key"},
            {id: "credentials.token", type: "SECRET"},
        ]
        const groups = {
            environment: {displayName: "Environment", description: "Pick env"},
            credentials: {displayName: "Credentials"},
        }
        expect(flattenInputs(unflattenToForms(leaves, groups))).toEqual(leaves)
    })

    it("places each FORM node at the position of its first child leaf (document order)", () => {
        const leaves = [
            {id: "a", type: "STRING"},
            {id: "env.region", type: "STRING"},
            {id: "b", type: "INT"},
        ]
        const tree = unflattenToForms(leaves, {env: {displayName: "Env"}})
        expect(tree.map(n => n.id)).toEqual(["a", "env", "b"])
        expect(tree[1].type).toBe("FORM")
        expect(tree[1].inputs?.map(c => c.id)).toEqual(["region"])
    })

    it("picks the longest matching form prefix for the owning form", () => {
        const tree = unflattenToForms([{id: "a.b.region", type: "STRING"}], {"a": {}, "a.b": {}})
        expect(tree).toHaveLength(1)
        expect(tree[0].id).toBe("a.b")
        expect(tree[0].inputs?.[0].id).toBe("region")
    })
})

describe("formChildName", () => {
    it("strips the owning form prefix off a dotted child id", () => {
        expect(formChildName("form.a", ["form"])).toBe("a")
        expect(formChildName("environment.region", ["environment"])).toBe("region")
    })

    it("leaves a non-form id untouched", () => {
        expect(formChildName("api_key", ["environment"])).toBe("api_key")
        expect(formChildName("api_key", [])).toBe("api_key")
    })

    it("does not misfire on a top-level id that itself contains a dot", () => {
        // `my.input` is a top-level (non-form) id; no form prefix matches, keep it whole
        expect(formChildName("my.input", ["environment"])).toBe("my.input")
        // a form id may itself contain a dot — strip the full prefix, not just up to the first dot
        expect(formChildName("my.form.region", ["my.form"])).toBe("region")
    })

    it("picks the longest matching form prefix", () => {
        expect(formChildName("a.b.region", ["a", "a.b"])).toBe("region")
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

    it("carries the FORM displayName separately from title", () => {
        const steps = buildWizardSteps([
            {id: "env", type: "FORM", displayName: "Environment", inputs: [{id: "region", type: "STRING"}]},
            {id: "creds", type: "FORM", inputs: [{id: "token", type: "SECRET"}]},
        ])
        expect(steps[0].displayName).toBe("Environment")
        expect(steps[0].title).toBe("Environment")
        expect(steps[1].displayName).toBeUndefined() // no displayName -> undefined, title falls back to id
        expect(steps[1].title).toBe("creds")
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
