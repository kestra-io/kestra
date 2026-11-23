import {afterEach, describe, expect, it} from "vitest"
import {isLoggedIn} from "./basicAuth"

function setCookieJar(cookie: string) {
    Object.defineProperty(document, "cookie", {
        configurable: true,
        get: () => cookie,
    })
}

afterEach(() => {
    Reflect.deleteProperty(document, "cookie")
})

describe("isLoggedIn", () => {
    it("returns true when the authentication flag is set", () => {
        setCookieJar("kestraBasicAuthenticated=true")

        expect(isLoggedIn()).toBe(true)
    })

    it("returns false when the cookie jar is empty", () => {
        setCookieJar("")

        expect(isLoggedIn()).toBe(false)
    })

    it("returns false when the authentication flag is false", () => {
        setCookieJar("kestraBasicAuthenticated=false")

        expect(isLoggedIn()).toBe(false)
    })

    it("does not match a cookie whose name ends with the authentication flag name", () => {
        setCookieJar("otherkestraBasicAuthenticated=true")

        expect(isLoggedIn()).toBe(false)
    })

    it.each([
        ["first", "kestraBasicAuthenticated=true; first=1; second=2"],
        ["middle", "first=1; kestraBasicAuthenticated=true; second=2"],
        ["last", "first=1; second=2; kestraBasicAuthenticated=true"],
    ])("finds the authentication flag when it is %s among other cookies", (_position, cookie) => {
        setCookieJar(cookie)

        expect(isLoggedIn()).toBe(true)
    })

    it("does not trim whitespace from the authentication flag value", () => {
        setCookieJar("kestraBasicAuthenticated= true")

        expect(isLoggedIn()).toBe(false)
    })
})
