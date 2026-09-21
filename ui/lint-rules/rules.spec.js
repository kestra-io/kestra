import {describe, it} from "vitest"
import {RuleTester} from "eslint"
import plugin from "./index.js"

// The rules run under oxlint (see .oxlintrc.json), which ships no rule tester of its own.
// ESLint's exercises the same plugin object, since oxlint consumes the ESLint rule shape.

const ruleTester = new RuleTester({languageOptions: {ecmaVersion: 2022, sourceType: "module"}})

const run = (name, tests) => it(name, () => ruleTester.run(name, plugin.rules[name], tests))

describe("kestra-test-hygiene", () => {
    run("no-unrestored-global-stub", {
        valid: [
            "vi.stubGlobal(\"Image\", X); afterAll(() => vi.unstubAllGlobals())",
            "vi.mock(\"x\", () => ({}))",
        ],
        invalid: [
            {code: "vi.stubGlobal(\"Image\", X)", errors: [{messageId: "unrestored"}]},
            // Restoring mocks is not the same as restoring stubbed globals.
            {code: "vi.stubGlobal(\"Image\", X); afterEach(() => vi.restoreAllMocks())", errors: [{messageId: "unrestored"}]},
        ],
    })

    run("no-unrestored-fake-timers", {
        valid: ["vi.useFakeTimers(); afterEach(() => vi.useRealTimers())"],
        invalid: [{code: "vi.useFakeTimers()", errors: [{messageId: "unrestored"}]}],
    })

    run("no-direct-global-assignment", {
        valid: [
            "vi.stubGlobal(\"matchMedia\", fn)",
            // Not a watched global, so not this rule's business.
            "window.myAppFlag = true",
            "const o = {}; o.matchMedia = fn",
        ],
        invalid: [
            {
                code: "import {vi} from \"vitest\"\nwindow.matchMedia = fn",
                output: "import {vi} from \"vitest\"\nvi.stubGlobal(\"matchMedia\", fn)",
                errors: [{messageId: "direct"}],
            },
            {
                code: "import {vi} from \"vitest\"\nglobalThis.fetch = vi.fn().mockResolvedValue(1)",
                output: "import {vi} from \"vitest\"\nvi.stubGlobal(\"fetch\", vi.fn().mockResolvedValue(1))",
                errors: [{messageId: "direct"}],
            },
            // No `vi` in scope, so it is reported but left unfixed.
            {code: "window.matchMedia = fn", output: null, errors: [{messageId: "direct"}]},
        ],
    })

    run("require-mock-reset", {
        valid: [
            "const m = vi.fn(); beforeEach(() => vi.clearAllMocks()); expect(m).toHaveBeenCalled()",
            "const m = vi.fn(); beforeEach(() => m.mockReset()); expect(m).toHaveBeenCalled()",
            // No call-count assertion, so a shared mock is fine.
            "const m = vi.fn(); expect(m()).toBe(1)",
            // Created per test, so nothing carries over.
            "it(\"x\", () => { const m = vi.fn(); expect(m).toHaveBeenCalled() })",
            // Factory mocks are rebuilt per file, not shared module state.
            "vi.mock(\"x\", () => ({go: vi.fn()})); expect(go).toHaveBeenCalled()",
        ],
        invalid: [
            {code: "const m = vi.fn(); expect(m).toHaveBeenCalledOnce()", errors: [{messageId: "unreset"}]},
            // Mocks nested in a module-level object literal are shared just the same.
            {code: "const store = {load: vi.fn()}; expect(store.load).toHaveBeenCalledTimes(1)", errors: [{messageId: "unreset"}]},
        ],
    })
})
