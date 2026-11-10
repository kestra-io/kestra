import {describe, expect, it} from "vitest"
import "./monacoEnvironment"

describe("monacoEnvironment", () => {
    it("defines window.MonacoEnvironment.getWorker so Monaco can spawn web workers", () => {
        expect(typeof window.MonacoEnvironment?.getWorker).toBe("function")
    })
})
