import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {setSelectedTenant} from "@kestra-io/kestra-sdk/shared"
import {fetchContextSections, resetContextSectionsCache} from "./fetchContextSections"
import type {ContextSectionProvider, DataSection} from "./types"

const t = (key: string) => key

function section(key: string): DataSection {
    return {key, label: key, chips: [{label: key, expr: `{{ ${key} }}`}]}
}

beforeEach(() => {
    resetContextSectionsCache()
})

afterEach(() => {
    setSelectedTenant("main")
})

describe("fetchContextSections", () => {
    it("omits a section whose provider rejects, without throwing, but logs it", async () => {
        const consoleError = vi.spyOn(console, "error").mockImplementation(() => {})
        const ok: ContextSectionProvider = async () => section("ok")
        const failing: ContextSectionProvider = async () => {
            throw new Error("boom")
        }

        const result = await fetchContextSections([ok, failing], {namespace: "team.a", t})

        expect(result).toEqual([section("ok")])
        expect(consoleError).toHaveBeenCalledWith("Context section provider failed", expect.any(Error))
        consoleError.mockRestore()
    })

    it("scopes the cache by the real, current tenant — not a caller-supplied field nothing populates", async () => {
        setSelectedTenant("tenant-a")
        const providerA = vi.fn(async () => section("secret-a"))
        const resultA = await fetchContextSections([providerA], {namespace: "team.a", t})
        expect(resultA).toEqual([section("secret-a")])

        setSelectedTenant("tenant-b")
        const providerB = vi.fn(async () => section("secret-b"))
        const resultB = await fetchContextSections([providerB], {namespace: "team.a", t})

        expect(providerB).toHaveBeenCalledTimes(1)
        expect(resultB).toEqual([section("secret-b")])
        expect(resultB).not.toEqual(resultA)
    })

    it("omits a section whose provider resolves to null", async () => {
        const empty: ContextSectionProvider = async () => null
        const result = await fetchContextSections([empty], {namespace: "team.a", t})
        expect(result).toEqual([])
    })

    it("caches the combined result per namespace, calling each provider only once", async () => {
        const provider = vi.fn(async () => section("kv"))

        await fetchContextSections([provider], {namespace: "team.a", t})
        await fetchContextSections([provider], {namespace: "team.a", t})

        expect(provider).toHaveBeenCalledTimes(1)
    })

    it("fetches independently per namespace", async () => {
        const provider = vi.fn(async () => section("kv"))

        await fetchContextSections([provider], {namespace: "team.a", t})
        await fetchContextSections([provider], {namespace: "team.b", t})

        expect(provider).toHaveBeenCalledTimes(2)
    })

    it("includes sections from extra (e.g. EE) providers alongside the native ones", async () => {
        const native: ContextSectionProvider = async () => section("kv")
        const extra: ContextSectionProvider = async () => section("credentials")

        const result = await fetchContextSections([native, extra], {namespace: "team.a", t})

        expect(result).toEqual([section("kv"), section("credentials")])
    })
})
