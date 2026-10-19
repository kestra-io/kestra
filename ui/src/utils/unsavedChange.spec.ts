import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"

import installUnsavedChangeGuard from "./unsavedChange"
import {useUnsavedChangesStore} from "../stores/unsavedChanges"

type BeforeUnloadHandler = (e: {returnValue?: string}) => string | undefined

const install = (translate?: (key: string) => string) => {
    const addEventListenerSpy = vi
        .spyOn(window, "addEventListener")
        .mockImplementation(() => {})

    let beforeEachCallback: ((to: {path: string}, from: {path: string}) => Promise<boolean | undefined>) | undefined
    const router = {
        beforeEach: vi.fn((callback: any) => {
            beforeEachCallback = callback
            return () => {}
        }),
    }

    const app = {
        config: {
            globalProperties: {
                $t: translate,
            },
        },
    }

    installUnsavedChangeGuard(app as any, router as any)

    const beforeUnloadCall = addEventListenerSpy.mock.calls.find(([type]) => type === "beforeunload")
    const beforeUnload = beforeUnloadCall?.[1] as unknown as BeforeUnloadHandler

    return {
        app,
        router,
        beforeUnload,
        navigate: (to: string, from: string) => beforeEachCallback!({path: to} as any, {path: from} as any),
    }
}

describe("unsavedChange", () => {
    let store: ReturnType<typeof useUnsavedChangesStore>

    beforeEach(() => {
        setActivePinia(createPinia())
        store = useUnsavedChangesStore()
    })

    afterEach(() => {
        vi.restoreAllMocks()
    })

    describe("beforeunload", () => {
        it("leaves returnValue untouched when there is no unsaved change", () => {
            const {beforeUnload} = install(vi.fn((key: string) => key))

            const event: {returnValue?: string} = {}
            const returned = beforeUnload(event)

            expect(event.returnValue).toBeUndefined()
            expect(returned).toBeUndefined()
        })

        it("sets returnValue to the translated message and returns it when there is an unsaved change", () => {
            const {beforeUnload} = install(vi.fn(() => "You have unsaved changes"))
            store.unsavedChange = true

            const event: {returnValue?: string} = {}
            const returned = beforeUnload(event)

            expect(event.returnValue).toBe("You have unsaved changes")
            expect(returned).toBe("You have unsaved changes")
        })

        it("reads the translation when the event fires, not when the guard is installed", () => {
            // The guard installs before app.use(i18n), so $t only becomes available later.
            const {app, beforeUnload} = install(undefined)
            store.unsavedChange = true

            const $t = vi.fn(() => "late translation")
            app.config.globalProperties.$t = $t

            const event: {returnValue?: string} = {}
            const returned = beforeUnload(event)

            expect($t).toHaveBeenCalledWith("unsaved changed ?")
            expect(event.returnValue).toBe("late translation")
            expect(returned).toBe("late translation")
        })
    })

    describe("route guard", () => {
        it("allows navigation without showing the dialog when there is no unsaved change", async () => {
            const {navigate} = install(vi.fn())

            await expect(navigate("/flows", "/dashboard")).resolves.toBeUndefined()
            expect(store.isDialogVisible).toBe(false)
        })

        it("shows the dialog when navigating to a different path with an unsaved change", async () => {
            const {navigate} = install(vi.fn())
            store.unsavedChange = true

            const navigation = navigate("/flows", "/dashboard")
            expect(store.isDialogVisible).toBe(true)

            store.handleCancel()
            await expect(navigation).resolves.toBe(false)
        })

        it("clears the unsaved change and allows the navigation when the dialog is confirmed", async () => {
            const {navigate} = install(vi.fn())
            store.unsavedChange = true

            const navigation = navigate("/flows", "/dashboard")
            expect(store.isDialogVisible).toBe(true)

            store.handleLeave()

            await expect(navigation).resolves.toBe(true)
            expect(store.unsavedChange).toBe(false)
        })

        it("returns false and keeps the unsaved change when the dialog is cancelled", async () => {
            const {navigate} = install(vi.fn())
            store.unsavedChange = true

            const navigation = navigate("/flows", "/dashboard")
            expect(store.isDialogVisible).toBe(true)

            store.handleCancel()

            await expect(navigation).resolves.toBe(false)
            expect(store.unsavedChange).toBe(true)
            expect(store.isDialogVisible).toBe(false)
        })

        it("does not show the dialog when navigating to the same path", async () => {
            const {navigate} = install(vi.fn())
            store.unsavedChange = true

            await expect(navigate("/flows", "/flows")).resolves.toBeUndefined()
            expect(store.isDialogVisible).toBe(false)
            expect(store.unsavedChange).toBe(true)
        })
    })
})
