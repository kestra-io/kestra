import {describe, it, expect} from "vitest"
import {isReauthOpen, requestReauth, resolveReauth} from "../../../src/composables/useReauthDialog"

describe("useReauthDialog", () => {
    it("shares one dialog between concurrent 401s and settles them all with the same outcome", async () => {
        const first = requestReauth()
        const second = requestReauth()
        expect(isReauthOpen()).toBe(true)

        resolveReauth(true)

        await expect(Promise.all([first, second])).resolves.toEqual([true, true])
        expect(isReauthOpen()).toBe(false)
    })

    it("opens a fresh dialog once the previous one is settled", async () => {
        const abandoned = requestReauth()
        resolveReauth(false)
        await expect(abandoned).resolves.toBe(false)

        const next = requestReauth()
        expect(isReauthOpen()).toBe(true)
        resolveReauth(true)
        await expect(next).resolves.toBe(true)
    })
})
