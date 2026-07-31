import {isModuleScope, isViCall} from "../utils.js"

const RESET_ALL = ["clearAllMocks", "resetAllMocks", "restoreAllMocks"]
const RESET_ONE = new Set(["mockReset", "mockClear", "mockRestore"])
const CALL_ASSERTIONS = new Set([
    "toHaveBeenCalled", "toHaveBeenCalledOnce", "toHaveBeenCalledTimes",
    "toHaveBeenCalledWith", "toHaveBeenNthCalledWith", "toHaveBeenLastCalledWith",
])

export default {
    meta: {
        type: "problem",
        schema: [],
        docs: {description: "Reset module-level vi.fn() mocks when asserting on their calls"},
        messages: {
            unreset: "This vi.fn() is created once for the whole file, but the file asserts on call counts and never resets it — the assertions then only hold in declaration order. Reset in beforeEach (vi.clearAllMocks()).",
        },
    },
    create(context) {
        const sharedMocks = []
        let hasReset = false
        let hasCallAssertion = false

        return {
            CallExpression(node) {
                if (isViCall(node, "fn") && isModuleScope(context.sourceCode, node)) {
                    sharedMocks.push(node)
                    return
                }
                if (RESET_ALL.some((name) => isViCall(node, name))) {
                    hasReset = true
                    return
                }
                if ("MemberExpression" !== node.callee.type || node.callee.computed) return
                if ("Identifier" !== node.callee.property.type) return

                const method = node.callee.property.name
                if (RESET_ONE.has(method)) hasReset = true
                else if (CALL_ASSERTIONS.has(method)) hasCallAssertion = true
            },
            "Program:exit"() {
                if (hasReset || !hasCallAssertion || !sharedMocks.length) return
                context.report({node: sharedMocks[0], messageId: "unreset"})
            },
        }
    },
}
