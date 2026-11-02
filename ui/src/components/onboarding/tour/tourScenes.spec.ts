import {describe, expect, it, vi} from "vitest"

import {
    TOUR_SCENES,
    TourDockerUnavailableError,
    TourSceneError,
    checkFirstExecution,
    sceneIdsOf,
    stepGroupsOf,
    workingLabelKey,
    type TourScene,
} from "./tourScenes"
import {
    TOUR_FLOW_ID,
    TOUR_NAMESPACE,
    TOUR_FLOW_BASE,
    TOUR_NOTIFY_TASK_BROKEN,
    TOUR_NOTIFY_TASK_FIXED,
    TOUR_TEST_EVENT_PAYLOAD,
    TOUR_SLACK_MOCK_URL,
    TOUR_SLACK_VARIABLE,
    tourFlowSource,
    tourWebhookTrigger,
    type TourTaskRunner,
} from "./tourFlows"
import en from "../../../translations/en.json"

const translations = (en as {
    en: {
        onboarding: {
            tour: {
                scenes: Record<string, Record<string, string>>
                steps: Record<string, string>
                [key: string]: unknown
            }
        }
    }
}).en.onboarding.tour

const TOUR_FLOW_PARAMS = {namespace: TOUR_NAMESPACE, id: TOUR_FLOW_ID}

const RUNNERS: TourTaskRunner[] = ["docker", "process"]

const PROCESS_RUNNER = "    taskRunner:\n      type: io.kestra.plugin.core.runner.Process"

const sceneOf = (id: string) => TOUR_SCENES.find((candidate) => candidate.id === id) as TourScene

const storeWith = (tour: Record<string, unknown> = {}) => {
    const state = {tour: {taskRunner: "docker", rejectedExecutionId: null, ...tour}}
    return {
        state,
        setTourState: vi.fn((patch: Record<string, unknown>) => Object.assign(state.tour, patch)),
    }
}

const completedOn = (
    id: string,
    route: {name: string; params?: Record<string, unknown>},
    tour: Record<string, unknown> = {},
) => {
    const context = {route: {params: {}, ...route}, store: storeWith(tour)}
    return Boolean(sceneOf(id).completedByUser?.(context as unknown as Parameters<NonNullable<TourScene["completedByUser"]>>[0]))
}

const executionOf = (id: string, state: string) => ({id, state: {current: state}})

const contextWith = (
    {tour = {}, execution = executionOf("exec-1", "SUCCESS"), dockerUnavailable = false}:
    {tour?: Record<string, unknown>; execution?: unknown; dockerUnavailable?: boolean} = {},
) => {
    const store = storeWith(tour)
    const actions = {
        executeFlow: vi.fn().mockResolvedValue(execution),
        waitForExecution: vi.fn().mockResolvedValue(execution),
        applySource: vi.fn().mockResolvedValue(undefined),
        dockerUnavailable: vi.fn().mockResolvedValue(dockerUnavailable),
    }
    return {
        store,
        actions,
        signal: new AbortController().signal,
        onStillRunning: vi.fn(),
    } as unknown as Parameters<NonNullable<TourScene["action"]>>[0] & {store: ReturnType<typeof storeWith>; actions: typeof actions}
}

const verifyOn = (id: string, context: ReturnType<typeof contextWith>, executionId = "exec-1") =>
    sceneOf(id).verify!({...context, route: {params: {id: executionId}}} as unknown as Parameters<NonNullable<TourScene["verify"]>>[0])

describe("product tour scenes", () => {
    it("has unique scene ids", () => {
        const ids = sceneIdsOf(TOUR_SCENES)
        expect(new Set(ids).size).toBe(ids.length)
    })

    it("groups the steps without ever going backwards", () => {
        const steps = TOUR_SCENES.map((scene) => scene.step)

        expect(Math.min(...steps)).toBe(1)
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
            .filter(([, copy]) => copy.callout)
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

    it("leaves the copilot step until the flow lands in an editor", () => {
        expect(completedOn("copilot", {name: "ai"})).toBe(false)
        expect(completedOn("copilot", {name: "flows/list"})).toBe(false)
        expect(completedOn("copilot", {name: "executions/list"})).toBe(false)

        expect(completedOn("copilot", {name: "flows/create"})).toBe(true)
        expect(completedOn("copilot", {name: "flows/update", params: TOUR_FLOW_PARAMS})).toBe(true)
    })

    it("follows the user into the tour flow only, not any flow", () => {
        const otherFlow = {namespace: "other", id: "other"}

        expect(completedOn("first_execution", {name: "flows/update", params: TOUR_FLOW_PARAMS})).toBe(true)
        expect(completedOn("first_execution", {name: "flows/update", params: otherFlow})).toBe(false)

        expect(completedOn("webhook_trigger", {name: "flows/update/triggers", params: TOUR_FLOW_PARAMS})).toBe(true)
        expect(completedOn("webhook_trigger", {name: "flows/update/triggers", params: otherFlow})).toBe(false)
    })

    it("names every step, for the plan listed on the intro card", () => {
        for (const group of stepGroupsOf(TOUR_SCENES)) {
            expect(
                translations.steps[String(group.step)],
                `missing name for step ${group.step}`,
            ).toBeTruthy()
        }
    })
})

describe("first execution outcome", () => {
    it("accepts a successful execution", async () => {
        const context = contextWith()

        await expect(checkFirstExecution(context, executionOf("exec-1", "SUCCESS"))).resolves.toBeUndefined()
        expect(context.actions.dockerUnavailable).not.toHaveBeenCalled()
    })

    it("explains a missing Docker socket when the execution failed because of it", async () => {
        const context = contextWith({dockerUnavailable: true})

        await expect(checkFirstExecution(context, executionOf("exec-1", "FAILED")))
            .rejects.toBeInstanceOf(TourDockerUnavailableError)
        expect(context.actions.dockerUnavailable).toHaveBeenCalledWith("exec-1")
    })

    it("offers to run without Docker and to read the docs", async () => {
        const context = contextWith({dockerUnavailable: true})

        const error = await checkFirstExecution(context, executionOf("exec-1", "FAILED")).catch((e) => e) as TourSceneError

        expect(error.options.recovery?.labelKey).toBe("onboarding.tour.actions.run_without_docker")
        expect(error.options.help?.href).toContain("https://kestra.io/docs/installation/docker")

        error.options.recovery?.run(context)
        expect(context.store.state.tour.taskRunner).toBe("process")
    })

    it("keeps the generic error when the failure is not about Docker", async () => {
        const context = contextWith({dockerUnavailable: false})

        const error = await checkFirstExecution(context, executionOf("exec-1", "FAILED")).catch((e) => e)

        expect(error).not.toBeInstanceOf(TourDockerUnavailableError)
        expect(error.key).toBe("onboarding.tour.errors.first_execution")
        expect(error.params).toEqual({state: "FAILED"})
    })

    it("does not blame Docker again once the Process runner is in use", async () => {
        const context = contextWith({tour: {taskRunner: "process"}, dockerUnavailable: true})

        const error = await checkFirstExecution(context, executionOf("exec-1", "FAILED")).catch((e) => e)

        expect(error).not.toBeInstanceOf(TourDockerUnavailableError)
        expect(error.key).toBe("onboarding.tour.errors.first_execution")
        expect(context.actions.dockerUnavailable).not.toHaveBeenCalled()
    })

    it("does not look for Docker on an execution that did not fail", async () => {
        const context = contextWith({dockerUnavailable: true})

        const error = await checkFirstExecution(context, executionOf("exec-1", "KILLED")).catch((e) => e)

        expect(error.key).toBe("onboarding.tour.errors.first_execution")
        expect(context.actions.dockerUnavailable).not.toHaveBeenCalled()
    })
})

describe("scenes that follow the user", () => {
    const executionRoute = (id: string) => ({
        name: "executions/update/logs",
        params: {namespace: TOUR_NAMESPACE, flowId: TOUR_FLOW_ID, id},
    })

    it("follows an execution of the tour flow", () => {
        expect(completedOn("flow_generated", executionRoute("exec-1"))).toBe(true)
        expect(completedOn("editor_help", executionRoute("exec-1"))).toBe(true)
    })

    it("does not follow the execution it already rejected, whichever tab shows it", () => {
        const tour = {rejectedExecutionId: "exec-1"}

        expect(completedOn("flow_generated", executionRoute("exec-1"), tour)).toBe(false)
        expect(completedOn("editor_help", executionRoute("exec-1"), tour)).toBe(false)
    })

    it("follows a new execution after one was rejected", () => {
        expect(completedOn("flow_generated", executionRoute("exec-2"), {rejectedExecutionId: "exec-1"})).toBe(true)
    })

    it("does not move on from the first execution when it failed", async () => {
        const context = contextWith({execution: executionOf("exec-1", "FAILED")})

        await expect(verifyOn("flow_generated", context)).rejects.toBeInstanceOf(TourSceneError)

        expect(context.store.state.tour.rejectedExecutionId).toBe("exec-1")
        expect(context.store.state.tour.lastExecutionId).toBeUndefined()
    })

    it("moves on from the first execution once it succeeded", async () => {
        const context = contextWith({tour: {rejectedExecutionId: "exec-0"}})

        await verifyOn("flow_generated", context)

        expect(context.actions.waitForExecution).toHaveBeenCalledWith("exec-1", expect.objectContaining({signal: context.signal}))
        expect(context.store.state.tour.lastExecutionId).toBe("exec-1")
        expect(context.store.state.tour.rejectedExecutionId).toBeNull()
    })

    it("rejects the execution the card ran when it failed", async () => {
        const context = contextWith({execution: executionOf("exec-1", "FAILED")})

        await expect(sceneOf("flow_generated").action!(context)).rejects.toBeInstanceOf(TourSceneError)

        expect(context.store.state.tour.rejectedExecutionId).toBe("exec-1")
    })

    it("only accepts a failed execution on the step that asks for a failure", async () => {
        const succeeded = contextWith({execution: executionOf("exec-1", "SUCCESS")})

        await expect(verifyOn("editor_help", succeeded)).rejects.toMatchObject({
            key: "onboarding.tour.errors.expected_failure",
        })
        expect(succeeded.store.state.tour.rejectedExecutionId).toBe("exec-1")

        const failed = contextWith({execution: executionOf("exec-1", "FAILED")})
        await verifyOn("editor_help", failed)
        expect(failed.store.state.tour.failedExecutionId).toBe("exec-1")
        expect(failed.store.state.tour.lastExecutionId).toBe("exec-1")
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
        expect(tourFlowSource.generated("docker")).toBe(TOUR_FLOW_BASE)

        expect(tourFlowSource.generated("docker")).not.toContain("variables:")
        expect(tourFlowSource.generated("docker")).not.toContain("notify")

        const broken = tourFlowSource.withBrokenNotify("docker")
        expect(broken).toContain(TOUR_NOTIFY_TASK_BROKEN)
        expect(broken.indexOf("variables:")).toBeLessThan(broken.indexOf("tasks:"))

        expect(tourFlowSource.withFixedNotify("docker")).toContain(TOUR_NOTIFY_TASK_FIXED)

        const withWebhook = tourFlowSource.withWebhook("order-events-test", "docker")
        expect(withWebhook).toContain(TOUR_NOTIFY_TASK_FIXED)
        expect(withWebhook).toContain("key: order-events-test")
        expect(withWebhook).not.toContain("log_event")
    })

    it("leaves the Docker task runner to the default", () => {
        for (const source of [
            tourFlowSource.generated("docker"),
            tourFlowSource.withBrokenNotify("docker"),
            tourFlowSource.withFixedNotify("docker"),
            tourFlowSource.withWebhook("key", "docker"),
        ]) {
            expect(source).not.toContain("taskRunner")
        }
    })

    it("runs the Python task on the Process runner at every stage", () => {
        for (const source of [
            tourFlowSource.generated("process"),
            tourFlowSource.withBrokenNotify("process"),
            tourFlowSource.withFixedNotify("process"),
            tourFlowSource.withWebhook("key", "process"),
        ]) {
            expect(source).toContain(`    type: io.kestra.plugin.scripts.python.Script\n${PROCESS_RUNNER}\n    dependencies:`)
            expect(source.match(/taskRunner/g)).toHaveLength(1)
        }
    })

    it("only changes the runner between the two variants", () => {
        const stages = {
            generated: (runner: TourTaskRunner) => tourFlowSource.generated(runner),
            broken: (runner: TourTaskRunner) => tourFlowSource.withBrokenNotify(runner),
            fixed: (runner: TourTaskRunner) => tourFlowSource.withFixedNotify(runner),
            webhook: (runner: TourTaskRunner) => tourFlowSource.withWebhook("key", runner),
        }

        for (const stage of Object.values(stages)) {
            expect(stage("process").replace(`\n${PROCESS_RUNNER}`, "")).toBe(stage("docker"))
        }
    })

    it("keeps the notify fix a one-expression change for both runners", () => {
        for (const runner of RUNNERS) {
            const broken = tourFlowSource.withBrokenNotify(runner)
            const fixed = tourFlowSource.withFixedNotify(runner)

            expect(fixed).toBe(broken.replace("vars.revenu }}", "vars.revenue }}"))
        }
    })

    it("keeps the flow runnable on any instance", () => {
        const source = tourFlowSource.withFixedNotify("docker")
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

describe("working label", () => {
    it("uses the scene's own label when it has one", () => {
        expect(workingLabelKey("scenes.a.working", "actions.running", (key) => key === "scenes.a.working"))
            .toBe("scenes.a.working")
    })

    it("falls back to the generic label otherwise", () => {
        expect(workingLabelKey("scenes.a.working", "actions.running", () => false)).toBe("actions.running")
    })
})
