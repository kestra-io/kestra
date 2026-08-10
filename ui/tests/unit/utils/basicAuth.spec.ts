import {describe, it, expect, vi, beforeEach, afterEach, afterAll} from "vitest"

const axiosPost = vi.fn().mockResolvedValue({data: {}})

vi.mock("@kestra-io/kestra-sdk", () => ({useClient: () => ({post: axiosPost})}))
vi.mock("override/utils/route", () => ({apiUrlWithoutTenants: () => "/api/v1"}))

import {signIn, logout, isLoggedIn} from "../../../src/utils/basicAuth"

afterAll(() => vi.unstubAllGlobals())

function setCookie(value: string) {
    document.cookie = value
}

function clearCookies() {
    // jsdom has no document.cookie reset API - expire every cookie the test may have set.
    document.cookie.split("; ").forEach((entry) => {
        const name = entry.split("=")[0]
        if (name) document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT`
    })
}

describe("isLoggedIn", () => {
    afterEach(() => clearCookies())

    it("returns false when the flag cookie is absent", () => {
        expect(isLoggedIn()).toBe(false)
    })

    it("returns true when the server-issued flag cookie is present", () => {
        setCookie("kestraBasicAuthenticated=true")
        expect(isLoggedIn()).toBe(true)
    })

    it("returns false once the flag cookie is cleared", () => {
        setCookie("kestraBasicAuthenticated=true")
        expect(isLoggedIn()).toBe(true)

        clearCookies()
        expect(isLoggedIn()).toBe(false)
    })
})

describe("signIn", () => {
    beforeEach(() => axiosPost.mockClear())

    it("posts trimmed credentials and does not touch sessionStorage", async () => {
        const sessionStorageSpy = vi.spyOn(Storage.prototype, "setItem")

        const result = await signIn({username: "  admin@kestra.io  ", password: "StrongPass1"})

        expect(axiosPost).toHaveBeenCalledWith("/api/v1/login", {username: "admin@kestra.io", password: "StrongPass1"}, expect.anything())
        expect(result).toEqual({username: "admin@kestra.io"})
        expect(sessionStorageSpy).not.toHaveBeenCalled()

        sessionStorageSpy.mockRestore()
    })

    it("rejects and does not swallow the error when credentials are invalid", async () => {
        axiosPost.mockRejectedValueOnce(new Error("unauthorized"))
        vi.stubGlobal("fetch", vi.fn().mockResolvedValue({}))

        await expect(signIn({username: "admin@kestra.io", password: "wrong"})).rejects.toThrow("unauthorized")
    })
})

describe("logout", () => {
    it("calls the logout endpoint with credentials included and returns true", async () => {
        const fetchMock = vi.fn().mockResolvedValue({})
        vi.stubGlobal("fetch", fetchMock)

        const result = await logout()

        expect(fetchMock).toHaveBeenCalledWith("/api/v1/logout", expect.objectContaining({
            method: "POST",
            credentials: "include",
        }))
        expect(result).toBe(true)
    })

    it("is best-effort: still resolves true when the request fails", async () => {
        vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("network down")))

        await expect(logout()).resolves.toBe(true)
    })
})
