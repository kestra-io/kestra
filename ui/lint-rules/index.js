import noDirectGlobalAssignment from "./rules/no-direct-global-assignment.js"
import noUnrestoredFakeTimers from "./rules/no-unrestored-fake-timers.js"
import noUnrestoredGlobalStub from "./rules/no-unrestored-global-stub.js"
import requireMockReset from "./rules/require-mock-reset.js"

// Static half of the leak protection: catches shared state a spec mutates in its own source.
// Leaks caused by the code under test are invisible here — tests/unit/leakGuard.ts catches those.
// Loaded by oxlint via `jsPlugins` in .oxlintrc.json (ESLint-compatible rule shape).
export default {
    meta: {name: "kestra-test-hygiene"},
    rules: {
        "no-direct-global-assignment": noDirectGlobalAssignment,
        "no-unrestored-fake-timers": noUnrestoredFakeTimers,
        "no-unrestored-global-stub": noUnrestoredGlobalStub,
        "require-mock-reset": requireMockReset,
    },
}
