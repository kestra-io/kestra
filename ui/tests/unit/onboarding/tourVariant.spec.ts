import {describe, expect, it} from "vitest"

import {
    TOUR_SCENES,
    sceneIdsOf,
    sceneIndexOf,
    stepGroupsOf,
} from "../../../src/components/onboarding/tour/tourScenes"
import {DEFAULT_TOUR_VARIANT} from "../../../src/components/onboarding/tour/tourVariant"
import {useTourVariant} from "../../../src/override/components/onboarding/tour/useTourVariant"
import en from "../../../src/translations/en.json"

const SCENES = [
    {id: "first", step: 1},
    {id: "second", step: 1},
    {id: "third", step: 2},
]

describe("tour scene helpers", () => {
    it("derives the step plan from any scene set", () => {
        expect(sceneIdsOf(SCENES)).toEqual(["first", "second", "third"])
        expect(stepGroupsOf(SCENES)).toEqual([
            {step: 1, scenes: ["first", "second"]},
            {step: 2, scenes: ["third"]},
        ])
    })

    it("resolves scene indexes, falling back to the first scene", () => {
        expect(sceneIndexOf(SCENES, "third")).toBe(2)
        expect(sceneIndexOf(SCENES, "a_scene_that_was_renamed")).toBe(0)
        expect(sceneIndexOf(SCENES, null)).toBe(0)
    })
})

describe("tour variant", () => {
    it("serves the default tour, wired to the scenes it ships with", () => {
        expect(useTourVariant()).toBe(DEFAULT_TOUR_VARIANT)
        expect(DEFAULT_TOUR_VARIANT.scenes).toBe(TOUR_SCENES)
        expect(DEFAULT_TOUR_VARIANT.id).toBe("product_tour")
    })

    it("reads its copy from the subtree its prefix points at", () => {
        const subtree = DEFAULT_TOUR_VARIANT.i18nPrefix
            .split(".")
            .reduce<any>((node, key) => node?.[key], (en as any).en)

        expect(subtree, `no copy under ${DEFAULT_TOUR_VARIANT.i18nPrefix}`).toBeDefined()

        // The keys TourOverlay reads outside of a scene, plus every scene's own title.
        for (const key of ["intro", "actions", "steps", "step_of", "menu", "nudge"]) {
            expect(subtree[key], `missing ${key}`).toBeDefined()
        }
        for (const scene of DEFAULT_TOUR_VARIANT.scenes) {
            expect(subtree.scenes[scene.id]?.title, `missing title for ${scene.id}`).toBeTruthy()
        }
    })

    it("offers itself from the route it auto-starts on", () => {
        const route = DEFAULT_TOUR_VARIANT.entryRoute("my-tenant") as any

        // The entry has to land on the auto-start route with the query the overlay consumes,
        // otherwise following the menu entry opens a page that never starts the tour.
        expect(route.name).toBe(DEFAULT_TOUR_VARIANT.autoStartRoute)
        expect(route.query).toEqual({tour: "start"})
        expect(route.params).toEqual({tenant: "my-tenant"})
    })
})
