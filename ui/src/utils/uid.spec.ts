import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {ensureUid, getUid} from "./uid"
import * as Utils from "./utils"

describe("uid", () => {
    beforeEach(() => {
        localStorage.clear()
    })

    afterEach(() => {
        localStorage.clear()
        vi.restoreAllMocks()
    })

    it("returns null when no uid is stored", () => {
        expect(getUid()).toBeNull()
    })

    it("returns the stored uid", () => {
        localStorage.setItem("uid", "existing-uid")

        expect(getUid()).toBe("existing-uid")
    })

    it("generates and persists a uid when storage is empty", () => {
        vi.spyOn(Utils, "uid").mockReturnValue("generated-uid")

        expect(ensureUid()).toBe("generated-uid")
        expect(localStorage.getItem("uid")).toBe("generated-uid")
    })

    it("returns the existing uid without overwriting it", () => {
        localStorage.setItem("uid", "existing-uid")
        const uidSpy = vi.spyOn(Utils, "uid")

        expect(ensureUid()).toBe("existing-uid")
        expect(localStorage.getItem("uid")).toBe("existing-uid")
        expect(uidSpy).not.toHaveBeenCalled()
    })

    it("returns the same uid on consecutive calls", () => {
        const uidSpy = vi.spyOn(Utils, "uid").mockReturnValue("generated-uid")

        expect(ensureUid()).toBe("generated-uid")
        expect(ensureUid()).toBe("generated-uid")
        expect(uidSpy).toHaveBeenCalledTimes(1)
    })
})
