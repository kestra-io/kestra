import {isViCall} from "../utils.js"

export default {
    meta: {
        type: "problem",
        schema: [],
        docs: {description: "Require vi.useRealTimers() in files that install fake timers"},
        messages: {
            unrestored: "vi.useFakeTimers() here is never undone, so fake timers stay installed for every later spec file (the unit suite runs with isolate: false). Add afterEach(() => vi.useRealTimers()).",
        },
    },
    create(context) {
        const installs = []
        let restored = false

        return {
            CallExpression(node) {
                if (isViCall(node, "useFakeTimers")) installs.push(node)
                else if (isViCall(node, "useRealTimers")) restored = true
            },
            "Program:exit"() {
                if (restored) return
                for (const install of installs) context.report({node: install, messageId: "unrestored"})
            },
        }
    },
}
