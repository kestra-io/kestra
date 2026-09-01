import {describe, it, expect, beforeEach, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import type {RouteLocationNormalized, RouteRecordNormalized} from "vue-router"

import {entityNotFoundGuard, type EntityResolver} from "../../../src/utils/routeEntityGuard"
import {useCoreStore} from "../../../src/stores/core"

const next = () => {}

function location(entity: EntityResolver | undefined, params: Record<string, string> = {tenant: "main", id: "io.kestra.missing"}) {
    const matched = entity ? [{meta: {entity}} as unknown as RouteRecordNormalized] : []
    return {params, matched} as unknown as RouteLocationNormalized
}

const elsewhere = location(undefined, {})

describe("entityNotFoundGuard", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        vi.spyOn(console, "error").mockImplementation(() => {})
    })

    it("renders the not-found screen at the requested URL when the entity 404s", async () => {
        const to = location(() => Promise.reject(Object.assign(new Error("404 Not Found"), {status: 404})))

        expect(await entityNotFoundGuard(to, elsewhere, next)).toBe(true)
        expect(useCoreStore().error).toBe(404)
    })

    it("treats a falsy resolution as not found, for loaders that map the 404 themselves", async () => {
        await entityNotFoundGuard(location(() => Promise.resolve(null)), elsewhere, next)

        expect(useCoreStore().error).toBe(404)
    })

    it("clears the not-found screen when the entity resolves", async () => {
        const coreStore = useCoreStore()
        coreStore.error = 404

        await entityNotFoundGuard(location(() => Promise.resolve({id: "io.kestra.exists"})), elsewhere, next)

        expect(coreStore.error).toBeUndefined()
    })

    it("lets the page mount on any other failure, which the interceptor has already toasted", async () => {
        const to = location(() => Promise.reject(Object.assign(new Error("500 Server Error"), {status: 500})))

        expect(await entityNotFoundGuard(to, elsewhere, next)).toBe(true)
        expect(useCoreStore().error).toBeUndefined()
    })

    it("discards the verdict of a navigation the user has already left", async () => {
        let rejectMissing: (error: unknown) => void = () => {}
        const missing = location(() => new Promise((_, reject) => {
            rejectMissing = reject
        }), {tenant: "main", id: "missing"})
        const existing = location(() => Promise.resolve({id: "io.kestra.exists"}), {tenant: "main", id: "exists"})

        const superseded = entityNotFoundGuard(missing, elsewhere, next)
        await entityNotFoundGuard(existing, elsewhere, next)
        rejectMissing(Object.assign(new Error("404 Not Found"), {status: 404}))
        await superseded

        expect(useCoreStore().error).toBeUndefined()
    })

    it("re-resolves when only the params change, since vue-router reuses the record", async () => {
        let resolved = 0
        const entity = () => {
            resolved++
            return Promise.resolve({id: "io.kestra.exists"})
        }
        const first = location(entity, {tenant: "main", id: "first"})
        const second = {...location(entity, {tenant: "main", id: "second"}), matched: first.matched} as RouteLocationNormalized

        await entityNotFoundGuard(first, elsewhere, next)
        await entityNotFoundGuard(second, first, next)

        expect(resolved).toBe(2)
    })

    it("does not re-resolve on a tab or filter change within the same entity", async () => {
        let resolved = 0
        const to = location(() => {
            resolved++
            return Promise.resolve({id: "io.kestra.exists"})
        })

        await entityNotFoundGuard(to, elsewhere, next)
        await entityNotFoundGuard(to, to, next)

        expect(resolved).toBe(1)
    })
})
