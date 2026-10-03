import {describe, expect, it} from "vitest"
import {
    KestraProblemError,
    ProblemTypes,
    asProblem,
    isProblemDetail,
    isProblemType,
    parseProblem,
    problemSlug,
    type ProblemDetail,
} from "@kestra-io/kestra-sdk"
import {
    problemBulkBody,
    problemDetail,
    problemFieldLabel,
    problemFieldMessage,
    problemTitle,
    toToastMessage,
} from "../../../src/utils/problem"

const problem = (overrides: Partial<ProblemDetail> = {}): ProblemDetail => ({
    type: "https://kestra.io/docs/api-reference/problems/entity-already-exists",
    title: "Entity already exists",
    status: 409,
    detail: "A flow with id 'my-flow' already exists in namespace 'company.team'.",
    instance: "/api/v1/main/flows",
    ...overrides,
})

describe("problemSlug", () => {
    it("extracts the slug from a full type URI", () => {
        expect(problemSlug(problem().type)).toBe("entity-already-exists")
    })


    it("treats the RFC's about:blank and a missing type as no type at all", () => {
        expect(problemSlug("about:blank")).toBeUndefined()
        expect(problemSlug(undefined)).toBeUndefined()
    })
})

describe("isProblemDetail", () => {
    it("recognises a problem document", () => {
        expect(isProblemDetail(problem())).toBe(true)
    })

    it("rejects the legacy error shapes", () => {
        expect(isProblemDetail({message: "Invalid entity", _embedded: {errors: []}})).toBe(false)
        expect(isProblemDetail({status: 403, message: "denied"})).toBe(false)
        expect(isProblemDetail("The execution is not terminated")).toBe(false)
        expect(isProblemDetail(null)).toBe(false)
    })
})

describe("asProblem", () => {
    it("reads through a KestraProblemError", () => {
        expect(asProblem(new KestraProblemError(problem()))?.title).toBe("Entity already exists")
    })

    it("reads through an axios-like response, as the useClient facade produces", () => {
        expect(asProblem({response: {data: problem()}})?.status).toBe(409)
    })

    it("reads a bare problem object, as a rejected 400 produces", () => {
        expect(asProblem(problem())?.status).toBe(409)
    })

    it("returns undefined for a non-problem error", () => {
        expect(asProblem(new Error("boom"))).toBeUndefined()
        expect(asProblem({response: {data: {message: "legacy"}}})).toBeUndefined()
    })
})

describe("KestraProblemError", () => {
    it("uses detail as the message, unprefixed, so raw err.message is presentable", () => {
        const error = new KestraProblemError(problem())
        expect(error.message).toBe("A flow with id 'my-flow' already exists in namespace 'company.team'.")
        expect(error.message).not.toMatch(/^409/)
    })

    it("falls back to the title when there is no detail", () => {
        expect(new KestraProblemError(problem({detail: undefined})).message).toBe("Entity already exists")
    })


    it("keeps the raw body reachable for extension members the interface omits", () => {
        const error = new KestraProblemError({...problem(), layout: {blocks: []}} as never)
        expect(error.problem.layout).toEqual({blocks: []})
    })
})

describe("isProblemType", () => {
    it("matches on the type, not on message prose", () => {
        const error = new KestraProblemError(problem())
        expect(isProblemType(error, ProblemTypes.ENTITY_ALREADY_EXISTS)).toBe(true)
        expect(isProblemType(error, ProblemTypes.ENTITY_NOT_FOUND)).toBe(false)
    })


    it("does not match a legacy body that merely mentions the words", () => {
        expect(isProblemType({response: {data: {message: "Flow id already exists: x"}}}, ProblemTypes.ENTITY_ALREADY_EXISTS))
            .toBe(false)
    })
})

describe("parseProblem", () => {
    it("parses a problem+json body, for call sites using fetch directly", () => {
        const parsed = parseProblem(JSON.stringify(problem()), 409, "application/problem+json")
        expect(parsed?.title).toBe("Entity already exists")
    })

    it("fills in the status when the body omits it", () => {
        const {status: _status, ...withoutStatus} = problem()
        expect(parseProblem(JSON.stringify(withoutStatus), 409, "application/problem+json")?.status).toBe(409)
    })

    it("returns undefined for a non-problem or unparsable body", () => {
        expect(parseProblem("not json at all", 500, "text/plain")).toBeUndefined()
        expect(parseProblem(JSON.stringify({message: "legacy"}), 422, "application/json")).toBeUndefined()
    })
})

describe("problemTitle", () => {
    const t = (key: string) => (key === "errors.problems.entity-already-exists.title" ? "Localized Title" : key)
    const te = (key: string) => key === "errors.problems.entity-already-exists.title"

    it("prefers a localized translation when a key exists", () => {
        expect(problemTitle(problem(), t, te)).toBe("Localized Title")
    })

    it("prefers the problem's own title when no translation key exists", () => {
        expect(problemTitle(problem({type: "https://kestra.io/docs/api-reference/problems/untranslated-type"}), t, () => false))
            .toBe("Entity already exists")
    })

    it("falls back to the generic error title when absent", () => {
        expect(problemTitle(problem({title: undefined, type: undefined}), t, () => false)).toBe("errors.generic.title")
        expect(problemTitle(undefined, t, () => false)).toBe("errors.generic.title")
    })
})

describe("problemDetail", () => {
    const t = (key: string) => (key === "errors.problems.service-unavailable.detail" ? "Localized detail" : key)
    const te = (key: string) => key === "errors.problems.service-unavailable.detail"

    it("returns the localized detail when a key exists", () => {
        const prob = problem({type: "https://kestra.io/docs/api-reference/problems/service-unavailable"})
        expect(problemDetail(prob, t, te)).toBe("Localized detail")
    })

    it("returns the detail string from the problem", () => {
        expect(problemDetail(problem(), t, () => false))
            .toBe("A flow with id 'my-flow' already exists in namespace 'company.team'.")
    })

    it("handles a problem with no detail or an undefined problem", () => {
        expect(problemDetail(problem({detail: undefined}), t, () => false)).toBe("")
        expect(problemDetail(undefined, t, () => false)).toBe("")
    })
})

describe("problemFieldLabel", () => {
    it("prefers the field path when present", () => {
        expect(problemFieldLabel({path: "tasks[my-task].type", detail: "invalid"})).toBe("tasks[my-task].type")
    })

    it("falls back to pointer when path is absent", () => {
        expect(problemFieldLabel({pointer: "/tasks/0/type", detail: "invalid"})).toBe("/tasks/0/type")
    })

    it("returns undefined when neither path nor pointer is present", () => {
        expect(problemFieldLabel({detail: "invalid"})).toBeUndefined()
    })
})

describe("problemFieldMessage", () => {
    const t = (key: string, named?: Record<string, unknown>) => `${key}:${named?.value ?? ""}`
    const te = (key: string) => key === "errors.problems.missing-field.detail"

    it("pairs a field with its violation message using a localized template", () => {
        const message = problemFieldMessage(
            {
                type: "https://kestra.io/docs/api-reference/problems/missing-field",
                path: "tasks[0].id",
                detail: "must not be blank",
            },
            t,
            te,
        )
        expect(message).toBe("errors.problems.missing-field.detail:tasks[0].id")
    })

    it("returns the raw detail when unlocalized", () => {
        const message = problemFieldMessage(
            {
                path: "tasks[0].id",
                detail: "must not be blank",
            },
            t,
            () => false,
        )
        expect(message).toBe("must not be blank")
    })
})

describe("problemBulkBody", () => {
    const t = (key: string) => key
    const te = () => false

    it("renders one entry per rejected item", () => {
        const body = problemBulkBody(
            problem({errors: [{detail: "Flow is invalid.", path: "my-flow"}]}),
            t,
            te,
        )
        expect(body).toEqual([{message: "Flow is invalid."}])
    })

    it("falls back to a message when the failure carries no items, rather than a blank toast body", () => {
        expect(problemBulkBody(problem({errors: []}), t, te)).toBe(problem().detail)
        expect(problemBulkBody(undefined, t, te)).toBe("errors.generic.content")
    })
})

describe("toToastMessage", () => {
    it("builds a toast message from a problem error", () => {
        const prob = problem()
        expect(toToastMessage(new KestraProblemError(prob))).toEqual({
            variant: "error",
            problem: prob,
            status: 409,
        })
    })

    it("supports custom variants", () => {
        const prob = problem()
        expect(toToastMessage(prob, "warning")).toEqual({
            variant: "warning",
            problem: prob,
            status: 409,
        })
    })

    it("degrades gracefully when error is not a problem document", () => {
        expect(toToastMessage(new Error("unexpected crash"))).toEqual({
            variant: "error",
            problem: undefined,
            status: undefined,
        })
        expect(toToastMessage("raw string error")).toEqual({
            variant: "error",
            problem: undefined,
            status: undefined,
        })
        expect(toToastMessage(null)).toEqual({
            variant: "error",
            problem: undefined,
            status: undefined,
        })
    })
})
