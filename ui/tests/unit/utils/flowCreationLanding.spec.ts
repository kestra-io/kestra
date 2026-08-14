import {describe, test, expect} from "vitest"
import {shouldShowLanding} from "../../../src/utils/flowCreationLanding"

describe("shouldShowLanding", () => {
    test("shows the funnel when /flows/new is opened without context", () => {
        expect(shouldShowLanding({})).toBe(true)
    })

    test("shows the funnel for a namespace-scoped create link, which the landing preselects", () => {
        expect(shouldShowLanding({namespace: "company.team"})).toBe(true)
    })

    test.each([
        ["blank", "true"],
        ["blueprintId", "mcp-tool-trigger"],
        ["blueprintSource", "community"],
        ["blueprintSourceYaml", "id: x"],
        ["copy", "true"],
        ["onboardingPreset", "true"],
        ["recipePreset", "true"],
        ["ai", "true"],
        ["createTrigger", "true"],
    ])("goes straight to the editor for ?%s", (key, value) => {
        expect(shouldShowLanding({[key]: value})).toBe(false)
    })

    test("goes straight to the editor when a param-driven key is present but empty", () => {
        expect(shouldShowLanding({copy: ""})).toBe(false)
    })
})
