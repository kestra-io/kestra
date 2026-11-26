import {afterEach, describe, expect, it} from "vitest"
import {computed, effectScope, nextTick} from "vue"
import {storageKeys} from "../utils/constants"
import {usePageSize} from "./usePageSize"

describe("usePageSize", () => {
    let scope = effectScope()

    afterEach(() => {
        scope.stop()
        localStorage.removeItem(storageKeys.PAGINATION_SIZE)
        scope = effectScope()
    })

    it("uses the URL size before the saved size", () => {
        localStorage.setItem(storageKeys.PAGINATION_SIZE, "50")
        const {pageSize} = scope.run(() => usePageSize(computed(() => 100)))!

        expect(pageSize.value).toBe(100)
    })

    it("uses the remembered size when the URL size is missing", async () => {
        const {pageSize, rememberPageSize} = scope.run(() => usePageSize(computed(() => undefined), 25))!

        rememberPageSize(100)

        expect(pageSize.value).toBe(100)
        await nextTick()
        expect(localStorage.getItem(storageKeys.PAGINATION_SIZE)).toBe("100")
    })

    it.each(["", "invalid"])("uses the default size when the saved value is %j", (savedSize) => {
        localStorage.setItem(storageKeys.PAGINATION_SIZE, savedSize)
        const {pageSize} = scope.run(() => usePageSize(computed(() => undefined), 40))!

        expect(pageSize.value).toBe(40)
    })
})
