import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {nextTick, watch} from "vue"

import {useBookmarkAction} from "./useBookmarkAction"

describe("useBookmarkAction", () => {
    beforeEach(() => {
        const {bookmarkAction, actionsMenuCount} = useBookmarkAction()
        bookmarkAction.value = undefined
        actionsMenuCount.value = 0
    })

    afterEach(() => {
        const {bookmarkAction, actionsMenuCount} = useBookmarkAction()
        bookmarkAction.value = undefined
        actionsMenuCount.value = 0
    })

    it("bookmarkAction starts undefined and actionsMenuCount starts 0", () => {
        const {bookmarkAction, actionsMenuCount} = useBookmarkAction()

        expect(bookmarkAction.value).toBeUndefined()
        expect(actionsMenuCount.value).toBe(0)
    })

    it("setting bookmarkAction through one caller is visible through another", () => {
        const first = useBookmarkAction()
        const second = useBookmarkAction()
        const action = {bookmarked: true, toggle: vi.fn()}

        first.bookmarkAction.value = action

        expect(second.bookmarkAction.value).toBe(action)
    })

    it("incrementing actionsMenuCount through one caller is visible through another", () => {
        const first = useBookmarkAction()
        const second = useBookmarkAction()

        first.actionsMenuCount.value++

        expect(second.actionsMenuCount.value).toBe(1)
    })

    it("bookmarkAction is a shallowRef, so replacing the object triggers an effect while mutating a field of it does not", async () => {
        const {bookmarkAction} = useBookmarkAction()
        const effect = vi.fn()
        const stop = watch(bookmarkAction, effect, {flush: "sync"})

        const action = {bookmarked: false, toggle: vi.fn()}
        bookmarkAction.value = action
        expect(effect).toHaveBeenCalledTimes(1)

        action.bookmarked = true
        await nextTick()
        expect(effect).toHaveBeenCalledTimes(1)

        bookmarkAction.value = {bookmarked: true, toggle: action.toggle}
        expect(effect).toHaveBeenCalledTimes(2)

        stop()
    })

    it("clearing bookmarkAction back to undefined works", () => {
        const {bookmarkAction} = useBookmarkAction()
        bookmarkAction.value = {bookmarked: true, toggle: vi.fn()}

        bookmarkAction.value = undefined

        expect(bookmarkAction.value).toBeUndefined()
    })
})

