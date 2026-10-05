import {describe, it, expect, vi} from "vitest"
import {isReauthOpen, recheckReauth, requestReauth, resolveReauth, submitReauth, useReauthDialog} from "../../../src/composables/useReauthDialog"

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

    it("confirms the session after a successful sign-in and rejects when it cannot be confirmed", async () => {
        const confirm = vi.fn().mockRejectedValueOnce(new Error("not signed in")).mockResolvedValue(undefined)
        const opened = requestReauth({signIn, confirm})

        await expect(submitReauth({username: "a", password: "wrong"})).rejects.toThrow("not signed in")
        await expect(submitReauth({username: "a", password: "right"})).resolves.toBeUndefined()
        expect(confirm).toHaveBeenCalledTimes(2)

        resolveReauth(false)
        await opened
    })

    it("closes on its own when the session comes back while the window was in the background", async () => {
        let signedIn = false
        const opened = requestReauth({loginUrl: "/ui/login", confirm: async () => {
            if (!signedIn) throw new Error("still signed out")
        }})

        await recheckReauth()
        expect(isReauthOpen()).toBe(true)

        signedIn = true
        window.dispatchEvent(new Event("focus"))

        await expect(opened).resolves.toBe(true)
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
