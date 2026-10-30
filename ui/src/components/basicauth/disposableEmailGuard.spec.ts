import {describe, test, expect, vi} from "vitest"
import {nextTick} from "vue"
import {useDisposableEmailGuard, type MailChecker} from "./disposableEmailGuard"

const listRejecting = {isValid: (email: string) => !email.endsWith("@mailinator.com")}

describe("useDisposableEmailGuard", () => {
    test("rejects every address until the list has answered", async () => {
        let release: (checker: MailChecker) => void = () => {}
        const guard = useDisposableEmailGuard(() => new Promise((resolve) => {
            release = resolve
        }))

        void guard.ensureLoaded()
        expect(guard.isAllowed("someone@kestra.io")).toBe(false)

        release(listRejecting)
        await nextTick()
        await Promise.resolve()
        expect(guard.isAllowed("someone@kestra.io")).toBe(true)
        expect(guard.isAllowed("someone@mailinator.com")).toBe(false)
    })

    test("accepts every address once the list has failed to load for good", async () => {
        const guard = useDisposableEmailGuard(() => Promise.reject(new Error("chunk 404")))

        await guard.ensureLoaded()

        // Failing closed here would leave the setup screen permanently unsubmittable.
        expect(guard.isAllowed("someone@mailinator.com")).toBe(true)
        expect(guard.isAllowed("")).toBe(false)
    })

    test("fetches the list once however often it is asked for", async () => {
        const load = vi.fn(() => Promise.resolve(listRejecting))
        const guard = useDisposableEmailGuard(load)

        await Promise.all([guard.ensureLoaded(), guard.ensureLoaded(), guard.ensureLoaded()])

        expect(load).toHaveBeenCalledTimes(1)
    })
})
