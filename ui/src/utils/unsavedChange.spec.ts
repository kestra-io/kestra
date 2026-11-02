import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import type {RouteLocation, Router} from "vue-router"
import {useUnsavedChangesStore} from "../stores/unsavedChanges"
import installUnsavedChange, {type TranslatableApp} from "./unsavedChange"

type BeforeUnloadHandler = (e: {returnValue?: unknown}) => unknown
type BeforeEachGuard = (to: RouteLocation, from: RouteLocation) => Promise<boolean | undefined>

const route = (path: string) => ({path}) as RouteLocation

function install(t: (key: string) => string = (key) => `translated:${key}`) {
    let beforeUnload: BeforeUnloadHandler | undefined
    vi.spyOn(window, "addEventListener").mockImplementation((type, listener) => {
        if (type === "beforeunload") {
            beforeUnload = listener as BeforeUnloadHandler
        }
    })

    let guard: BeforeEachGuard | undefined
    const router = {
        beforeEach: (callback: BeforeEachGuard) => {
            guard = callback
        },
    } as unknown as Router
    const app: TranslatableApp = {config: {globalProperties: {$t: t}}}

    installUnsavedChange(app, router)
    app.config.globalProperties.$t = t

    return {
        beforeUnload: beforeUnload!,
        guard: guard!,
        app,
    }
}

describe("unsavedChange", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
    })

    afterEach(() => {
        vi.restoreAllMocks()
    })

    it("leaves returnValue untouched and allows navigation when nothing is unsaved", async () => {
        const {beforeUnload, guard} = install()
        const store = useUnsavedChangesStore()
        const showDialog = vi.spyOn(store, "showDialog")
        const event: {returnValue?: unknown} = {}

        expect(beforeUnload(event)).toBeUndefined()
        expect(event.returnValue).toBeUndefined()
        expect(await guard(route("/flows"), route("/executions"))).toBeUndefined()
        expect(showDialog).not.toHaveBeenCalled()
    })

    it("sets and returns the translated message on beforeunload when changes are unsaved", () => {
        const {beforeUnload} = install()
        useUnsavedChangesStore().unsavedChange = true
        const event: {returnValue?: unknown} = {}

        const result = beforeUnload(event)

        expect(result).toBe("translated:unsaved changed ?")
        expect(event.returnValue).toBe("translated:unsaved changed ?")
    })

    it("reads the translation when beforeunload fires rather than at install time", () => {
        const {beforeUnload, app} = install()
        useUnsavedChangesStore().unsavedChange = true
        app.config.globalProperties.$t = () => "after"

        expect(beforeUnload({})).toBe("after")
    })

    it("shows the dialog when navigating to a different path with unsaved changes", () => {
        const {guard} = install()
        const store = useUnsavedChangesStore()
        store.unsavedChange = true

        guard(route("/flows"), route("/executions"))

        expect(store.isDialogVisible).toBe(true)
    })

    it("clears the unsaved change and allows navigation once the user confirms", async () => {
        const {guard} = install()
        const store = useUnsavedChangesStore()
        store.unsavedChange = true

        const navigation = guard(route("/flows"), route("/executions"))
        store.handleLeave()

        expect(await navigation).toBe(true)
        expect(store.unsavedChange).toBe(false)
    })

    it("blocks navigation and keeps the unsaved change when the user cancels", async () => {
        const {guard} = install()
        const store = useUnsavedChangesStore()
        store.unsavedChange = true

        const navigation = guard(route("/flows"), route("/executions"))
        store.handleCancel()

        expect(await navigation).toBe(false)
        expect(store.unsavedChange).toBe(true)
    })

    it("does not show the dialog when navigating to the same path", async () => {
        const {guard} = install()
        const store = useUnsavedChangesStore()
        store.unsavedChange = true

        const navigation = guard(route("/flows/edit"), route("/flows/edit"))

        expect(store.isDialogVisible).toBe(false)
        expect(await navigation).toBeUndefined()
        expect(store.unsavedChange).toBe(true)
    })
})
