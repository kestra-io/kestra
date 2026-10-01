import {describe, it, expect, vi} from "vitest"
import {isReauthOpen, requestReauth, resolveReauth, submitReauth, useReauthDialog} from "../../../src/composables/useReauthDialog"

describe("useReauthDialog", () => {
    const signIn = vi.fn().mockResolvedValue(undefined)

    it("shares one dialog between concurrent 401s and settles them all with the same outcome", async () => {
        const first = requestReauth({signIn})
        const second = requestReauth({signIn})
        expect(isReauthOpen()).toBe(true)

        resolveReauth(true)

        await expect(Promise.all([first, second])).resolves.toEqual([true, true])
        expect(isReauthOpen()).toBe(false)
    })

    it("signs in through the handler of the caller that opened the dialog", async () => {
        const opened = requestReauth({signIn})
        requestReauth({signIn: vi.fn()})

        await submitReauth({username: "a", password: "b"})

        expect(signIn).toHaveBeenCalledWith({username: "a", password: "b"})
        resolveReauth(false)
        await opened
    })

    it("offers the password form only when the caller supplies a sign-in", async () => {
        const withoutPassword = requestReauth({loginUrl: "/ui/login"})
        const {canSignInWithPassword, loginUrl} = useReauthDialog()

        expect(canSignInWithPassword.value).toBe(false)
        expect(loginUrl.value).toBe("/ui/login")
        await expect(submitReauth({username: "a", password: "b"})).rejects.toThrow()
        resolveReauth(false)
        await withoutPassword
    })

    it("opens a fresh dialog once the previous one is settled", async () => {
        const abandoned = requestReauth({signIn})
        resolveReauth(false)
        await expect(abandoned).resolves.toBe(false)

        const next = requestReauth({signIn})
        expect(isReauthOpen()).toBe(true)
        resolveReauth(true)
        await expect(next).resolves.toBe(true)
    })
})
