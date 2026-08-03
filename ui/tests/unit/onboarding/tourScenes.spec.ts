import {describe, expect, it} from "vitest"

import {
    TOUR_SCENES,
    TOUR_SCENE_IDS,
    TOUR_STEP_GROUPS,
    TOUR_STEP_GROUP_COUNT,
    TOUR_TOTAL_STEPS,
    tourSceneIndex,
} from "../../../src/components/onboarding/tour/tourScenes"
import {
    TOUR_FLOW_BASE,
    TOUR_NOTIFY_TASK_BROKEN,
    TOUR_NOTIFY_TASK_FIXED,
    TOUR_TEST_EVENT_PAYLOAD,
    TOUR_SLACK_MOCK_URL,
    TOUR_SLACK_VARIABLE,
    tourFlowSource,
    tourWebhookTrigger,
} from "../../../src/components/onboarding/tour/tourFlows"
import en from "../../../src/translations/en.json"

const translations = (en as any).en.onboarding.tour

describe("product tour scenes", () => {
    it("has unique scene ids", () => {
        expect(new Set(TOUR_SCENE_IDS).size).toBe(TOUR_SCENE_IDS.length)
    })

    it("counts one numbered step per scene", () => {
        expect(TOUR_TOTAL_STEPS).toBe(TOUR_SCENES.length)
        expect(TOUR_STEP_GROUPS.reduce((total, group) => total + group.scenes.length, 0))
            .toBe(TOUR_TOTAL_STEPS)
    })

    it("groups the steps without ever going backwards", () => {
        const steps = TOUR_SCENES.map((scene) => scene.step)

        expect(Math.min(...steps)).toBe(1)
        expect(Math.max(...steps)).toBe(TOUR_STEP_GROUP_COUNT)
        expect(TOUR_STEP_GROUPS.length).toBe(TOUR_STEP_GROUP_COUNT)
        expect([...steps]).toEqual([...steps].sort((a, b) => a - b))
    })

    it("translates every scene it shows", () => {
        for (const scene of TOUR_SCENES) {
            const copy = translations.scenes[scene.id]
            expect(copy, `missing translations for scene ${scene.id}`).toBeDefined()
            expect(copy.title, `missing title for ${scene.id}`).toBeTruthy()
            expect(copy.body, `missing body for ${scene.id}`).toBeTruthy()
            expect(copy.next, `missing next label for ${scene.id}`).toBeTruthy()

            if (scene.milestone) {
                expect(copy.milestone, `missing milestone for ${scene.id}`).toBeTruthy()
            }
            if (scene.callout) {
                expect(copy.callout, `missing callout for ${scene.id}`).toBeTruthy()
            }
        }
    })

    it("shows every callout it has copy for", () => {
        const withCopy = Object.entries(translations.scenes)
            .filter(([, copy]) => (copy as any).callout)
            .map(([id]) => id)
        const withFlag = TOUR_SCENES.filter((scene) => scene.callout).map((scene) => scene.id)

        expect(withCopy.toSorted()).toEqual(withFlag.toSorted())
    })

    it("keeps mustaches out of the copy", () => {
        // vue-i18n parses `{...}` as interpolation, so a literal `{{ ... }}` throws at render time.
        const strings: string[] = []
        const collect = (value: unknown) => {
            if (typeof value === "string") {
                strings.push(value)
            } else if (value && typeof value === "object") {
                Object.values(value).forEach(collect)
            }
        }
        collect(translations)

        expect(strings.filter((value) => value.includes("{{"))).toEqual([])
    })

    it("keeps markup to the strings that are rendered as HTML", () => {
        const rendersHtml = [
            /^scenes\.[a-z_]+\.(body|callout)$/,
            /^finale\.takeaways\.[a-z_]+\.body$/,
            /^errors\.[a-z_]+$/,
        ]

        const withMarkup: string[] = []
        const collect = (value: unknown, path: string) => {
            if (typeof value === "string") {
                if (/<[a-z]+[\s>]/.test(value)) {
                    withMarkup.push(path)
                }
            } else if (value && typeof value === "object") {
                Object.entries(value).forEach(([key, nested]) =>
                    collect(nested, path ? `${path}.${key}` : key),
                )
            }
        }
        collect(translations, "")

        const unrendered = withMarkup.filter(
            (path) => !rendersHtml.some((pattern) => pattern.test(path)),
        )
        expect(unrendered).toEqual([])
    })

    it("resolves scene indexes, falling back to the first scene", () => {
        expect(tourSceneIndex("copilot")).toBe(0)
        expect(tourSceneIndex("chain")).toBe(TOUR_SCENES.length - 1)
        expect(tourSceneIndex("a_scene_that_was_renamed")).toBe(0)
        expect(tourSceneIndex(null)).toBe(0)
    })

    it("only skips the work on the scenes that describe what is already on screen", () => {
        const withoutAction = TOUR_SCENES.filter((scene) => !scene.action)
        expect(withoutAction.map((scene) => scene.id)).toEqual([
            "first_execution",
            "add_task",
            "replayed_execution",
            "event_execution",
            "chain",
        ])
    })

    it("knows which steps the user can also do in the UI", () => {
        const followed = TOUR_SCENES.filter((scene) => scene.completedByUser).map((scene) => scene.id)

        expect(followed).toEqual([
            "copilot",
            "flow_generated",
            "first_execution",
            "editor_help",
            "failed_execution",
            "replayed_execution",
            "webhook_trigger",
            "test_event",
        ])
    })

    it("names every step, for the plan listed on the intro card", () => {
        for (const group of TOUR_STEP_GROUPS) {
            expect(
                translations.steps[String(group.step)],
                `missing name for step ${group.step}`,
            ).toBeTruthy()
        }
    })
})

describe("product tour flows", () => {
    it("breaks the notify task on an expression, and fixes exactly that", () => {
        expect(TOUR_NOTIFY_TASK_BROKEN).toContain("vars.revenu }}")
        expect(TOUR_NOTIFY_TASK_FIXED).toContain("vars.revenue }}")

        const difference = TOUR_NOTIFY_TASK_FIXED.length - TOUR_NOTIFY_TASK_BROKEN.length
        expect(difference).toBe(1)
    })

    it("builds each stage on top of the previous one", () => {
        expect(tourFlowSource.generated()).toBe(TOUR_FLOW_BASE)

        expect(tourFlowSource.generated()).not.toContain("variables:")
        expect(tourFlowSource.generated()).not.toContain("notify")

        const broken = tourFlowSource.withBrokenNotify()
        expect(broken).toContain(TOUR_NOTIFY_TASK_BROKEN)
        expect(broken.indexOf("variables:")).toBeLessThan(broken.indexOf("tasks:"))

        expect(tourFlowSource.withFixedNotify()).toContain(TOUR_NOTIFY_TASK_FIXED)

        const withWebhook = tourFlowSource.withWebhook("order-events-test")
        expect(withWebhook).toContain(TOUR_NOTIFY_TASK_FIXED)
        expect(withWebhook).toContain("key: order-events-test")
        expect(withWebhook).not.toContain("log_event")
    })

    it("keeps the flow runnable on any instance", () => {
        const source = tourFlowSource.withFixedNotify()
        expect(source).not.toContain("taskRunner")
        expect(source).toContain(`vars.${TOUR_SLACK_VARIABLE}`)
        expect(source).toContain(`${TOUR_SLACK_VARIABLE}: ${TOUR_SLACK_MOCK_URL}`)
        expect(source).not.toContain(`url: ${TOUR_SLACK_MOCK_URL}`)
        expect(source).toContain("namespace: tutorial")
    })

    it("declares the webhook trigger under triggers", () => {
        expect(tourWebhookTrigger("abc")).toContain("io.kestra.plugin.core.trigger.Webhook")
        expect(tourWebhookTrigger("abc")).toContain("triggers:")
    })

    it("prefills a valid JSON payload for the test event", () => {
        expect(() => JSON.parse(TOUR_TEST_EVENT_PAYLOAD)).not.toThrow()
    })
})
