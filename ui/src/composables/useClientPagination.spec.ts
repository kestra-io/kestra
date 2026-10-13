import {describe, expect, it} from "vitest"
import {computed, nextTick, ref} from "vue"

import {useClientPagination} from "./useClientPagination"

const rows = (count: number) => Array.from({length: count}, (_, i) => `row-${i + 1}`)

describe("useClientPagination", () => {
    it("returns the first window of the source at the default page size", () => {
        const {paged} = useClientPagination(computed(() => rows(30)))

        expect(paged.value).toHaveLength(25)
        expect(paged.value[0]).toBe("row-1")
        expect(paged.value[24]).toBe("row-25")
    })

    it("slices the source when the page changes", async () => {
        const {currentPage, paged} = useClientPagination(computed(() => rows(30)))

        currentPage.value = 2
        await nextTick()

        expect(paged.value).toHaveLength(5)
        expect(paged.value[0]).toBe("row-26")
    })

    it("bounces back to page 1 when the source shrinks below the current window", async () => {
        const listLength = ref(60)
        const list = computed(() => rows(listLength.value))
        const {currentPage, paged} = useClientPagination(list)

        currentPage.value = 3
        await nextTick()
        expect(paged.value[0]).toBe("row-51")

        listLength.value = 40
        await nextTick()

        expect(currentPage.value).toBe(1)
        expect(paged.value[0]).toBe("row-1")
    })

    it("keeps the current page when a shrink still leaves it inside the window", async () => {
        const listLength = ref(60)
        const list = computed(() => rows(listLength.value))
        const {currentPage, paged} = useClientPagination(list)

        currentPage.value = 2
        await nextTick()

        listLength.value = 50
        await nextTick()

        expect(currentPage.value).toBe(2)
        expect(paged.value[0]).toBe("row-26")
    })
})
