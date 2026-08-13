import {afterEach, describe, expect, test, vi} from "vitest"
import {defineComponent} from "vue"
import {mount} from "@vue/test-utils"
import type {RouteLocation} from "vue-router"
import useRestoreUrl, {getRestoredQuery} from "../../../src/composables/useRestoreUrl"

const mocks = vi.hoisted(() => ({
    route: {
        name: "executions/list",
        params: {} as Record<string, string>,
        query: {} as Record<string, string>,
        fullPath: "/executions",
    },
    replace: vi.fn(),
}))

vi.mock("vue-router", () => ({
    useRoute: () => mocks.route,
    useRouter: () => ({replace: mocks.replace}),
}))

const STORAGE_KEY = "executions_list_restore_url"

function mountComposable() {
    let api!: ReturnType<typeof useRestoreUrl>
    const Comp = defineComponent({
        setup() {
            api = useRestoreUrl()
            return () => null
        },
    })
    mount(Comp)
    return api
}

describe("useRestoreUrl pagination exclusion", () => {
    afterEach(() => {
        window.sessionStorage.clear()
        mocks.route.query = {}
        mocks.replace.mockClear()
    })

    test("saveRestoreUrl does not persist the page number", () => {
        mocks.route.query = {namespace: "company", page: "5"}

        const {saveRestoreUrl} = mountComposable()
        saveRestoreUrl()

        expect(JSON.parse(window.sessionStorage.getItem(STORAGE_KEY)!)).toEqual({namespace: "company"})
    })

    test("saveRestoreUrl clears the saved state when only the page number remains", () => {
        window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify({namespace: "company"}))
        mocks.route.query = {page: "5"}

        const {saveRestoreUrl} = mountComposable()
        saveRestoreUrl()

        expect(window.sessionStorage.getItem(STORAGE_KEY)).toBeNull()
    })

    test("getRestoredQuery ignores a page number persisted before the key became non-restorable", () => {
        window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify({namespace: "company", page: "5"}))

        const {query, change} = getRestoredQuery(mocks.route as unknown as RouteLocation)

        expect(change).toBe(true)
        expect(query).toEqual({namespace: "company"})
    })
})
