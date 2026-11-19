import {describe, it, expect, vi} from "vitest"
import resource from "../../models/resource"
import action from "../../models/action"
import {isFlowTabAllowed} from "./flowTabs"

const NAMESPACE = "company.team"

function makeUser({hasAny = () => true, isAllowed = () => true}: {
    hasAny?: (resource: string) => boolean
    isAllowed?: (resource: string, action: string, namespace: string) => boolean
} = {}) {
    return {
        hasAny: vi.fn(hasAny),
        isAllowed: vi.fn(isAllowed),
    } as unknown as Parameters<typeof isFlowTabAllowed>[1]["user"] & {
        hasAny: ReturnType<typeof vi.fn>
        isAllowed: ReturnType<typeof vi.fn>
    }
}

const EXECUTION_TABS = ["executions", "logs", "metrics"]
const FLOW_TABS = ["edit", "revisions", "triggers", "dependencies"]
const NAMESPACE_GATED_TABS = [...EXECUTION_TABS, ...FLOW_TABS]
const GATED_TABS = ["overview", ...NAMESPACE_GATED_TABS]

describe("isFlowTabAllowed", () => {
    describe("overview", () => {
        it("is allowed when the user has any EXECUTION action", () => {
            const user = makeUser({hasAny: (r) => r === resource.EXECUTION})
            expect(isFlowTabAllowed("overview", {user, namespace: NAMESPACE})).toBe(true)
            expect(user.hasAny).toHaveBeenCalledWith(resource.EXECUTION)
        })

        it("is refused when the user has no EXECUTION action", () => {
            const user = makeUser({hasAny: () => false})
            expect(isFlowTabAllowed("overview", {user, namespace: NAMESPACE})).toBe(false)
        })
    })

    describe.each(EXECUTION_TABS)("%s", (tab) => {
        it("is allowed with EXECUTION VIEW on the flow's namespace", () => {
            const user = makeUser({isAllowed: (r, a) => r === resource.EXECUTION && a === action.VIEW})
            expect(isFlowTabAllowed(tab, {user, namespace: NAMESPACE})).toBe(true)
            expect(user.isAllowed).toHaveBeenCalledWith(resource.EXECUTION, action.VIEW, NAMESPACE)
        })

        it("is refused with only FLOW VIEW", () => {
            const user = makeUser({isAllowed: (r, a) => r === resource.FLOW && a === action.VIEW})
            expect(isFlowTabAllowed(tab, {user, namespace: NAMESPACE})).toBe(false)
        })
    })

    describe.each(FLOW_TABS)("%s", (tab) => {
        it("is allowed with FLOW VIEW on the flow's namespace", () => {
            const user = makeUser({isAllowed: (r, a) => r === resource.FLOW && a === action.VIEW})
            expect(isFlowTabAllowed(tab, {user, namespace: NAMESPACE})).toBe(true)
            expect(user.isAllowed).toHaveBeenCalledWith(resource.FLOW, action.VIEW, NAMESPACE)
        })

        it("is refused with only EXECUTION VIEW", () => {
            const user = makeUser({isAllowed: (r, a) => r === resource.EXECUTION && a === action.VIEW})
            expect(isFlowTabAllowed(tab, {user, namespace: NAMESPACE})).toBe(false)
        })
    })

    it.each(GATED_TABS)("refuses %s when there is no user", (tab) => {
        expect(isFlowTabAllowed(tab, {user: undefined, namespace: NAMESPACE})).toBe(false)
    })

    it.each(NAMESPACE_GATED_TABS)("refuses %s when the namespace is undefined, without asking isAllowed", (tab) => {
        const user = makeUser()
        expect(isFlowTabAllowed(tab, {user, namespace: undefined})).toBe(false)
        expect(user.isAllowed).not.toHaveBeenCalled()
    })

    it("checks permissions against the namespace it is given", () => {
        const user = makeUser({isAllowed: (_r, _a, ns) => ns === "other.namespace"})
        expect(isFlowTabAllowed("edit", {user, namespace: "other.namespace"})).toBe(true)
        expect(isFlowTabAllowed("edit", {user, namespace: NAMESPACE})).toBe(false)
        expect(user.isAllowed).toHaveBeenNthCalledWith(1, resource.FLOW, action.VIEW, "other.namespace")
        expect(user.isAllowed).toHaveBeenNthCalledWith(2, resource.FLOW, action.VIEW, NAMESPACE)
    })

    it("allows a tab name it does not recognise, even without a user", () => {
        expect(isFlowTabAllowed("some-new-tab", {user: undefined, namespace: undefined})).toBe(true)
    })
})
