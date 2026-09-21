import {isViCall} from "../utils.js"

export default {
    meta: {
        type: "problem",
        schema: [],
        docs: {description: "Require vi.unstubAllGlobals() in files that stub globals"},
        messages: {
            unrestored: "vi.stubGlobal() here is never undone, so the stub leaks into every later spec file (the unit suite runs with isolate: false). Add afterAll(() => vi.unstubAllGlobals()).",
        },
    },
    create(context) {
        const stubs = []
        let restored = false

        return {
            CallExpression(node) {
                if (isViCall(node, "stubGlobal")) stubs.push(node)
                else if (isViCall(node, "unstubAllGlobals")) restored = true
            },
            "Program:exit"() {
                if (restored) return
                for (const stub of stubs) context.report({node: stub, messageId: "unrestored"})
            },
        }
    },
}
