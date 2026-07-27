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
        // What the card shows: "step 5 of 13", with 13 ticks in the progress bar.
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

            // The last scene ends the tour from its primary button like any other.
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
        // The other way round from the check above: a callout in the translations that no scene asks
        // for is copy nobody ever reads.
        const withCopy = Object.entries(translations.scenes)
            .filter(([, copy]) => (copy as any).callout)
            .map(([id]) => id)
        const withFlag = TOUR_SCENES.filter((scene) => scene.callout).map((scene) => scene.id)

        expect(withCopy.toSorted()).toEqual(withFlag.toSorted())
    })

    it("keeps mustaches out of the copy", () => {
        // vue-i18n parses `{...}` as interpolation, so a literal `{{ ... }}` in a message throws
        // while the card renders. Expression examples belong in <code> without the braces.
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
        // Everything else is interpolated, where a <strong> would be shown as its own tags.
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
        // These hand over to the scene that follows, which does the work: the editor is opened by
        // the scene that needs it, after the Docs panel has been put in its layout. `chain` is the
        // last scene, where the button ends the tour.
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
        // These are the steps whose action has a control of its own on screen. The tour follows the
        // user there instead of asking for its own button as well.
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

        // The generated flow is two tasks and nothing else, so what the prompt asks for is what the
        // first card describes. The Slack variable arrives with the task that uses it.
        expect(tourFlowSource.generated()).not.toContain("variables:")
        expect(tourFlowSource.generated()).not.toContain("notify")

        const broken = tourFlowSource.withBrokenNotify()
        expect(broken).toContain(TOUR_NOTIFY_TASK_BROKEN)
        expect(broken.indexOf("variables:")).toBeLessThan(broken.indexOf("tasks:"))

        expect(tourFlowSource.withFixedNotify()).toContain(TOUR_NOTIFY_TASK_FIXED)

        const withWebhook = tourFlowSource.withWebhook("order-events-test")
        expect(withWebhook).toContain(TOUR_NOTIFY_TASK_FIXED)
        expect(withWebhook).toContain("key: order-events-test")
        // The payload is read in the execution's context, so no task logs it.
        expect(withWebhook).not.toContain("log_event")
    })

    it("keeps the flow runnable on any instance", () => {
        const source = tourFlowSource.withFixedNotify()

        // No task runner is pinned, so the Python task uses the default one.
        expect(source).not.toContain("taskRunner")
        // The Slack URL is a flow variable: no credentials needed, nothing stored on the instance,
        // and no editor warning about a plain value in a property annotated as a secret.
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
