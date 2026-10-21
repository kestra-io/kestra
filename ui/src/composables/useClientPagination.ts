import {computed, ref, watch} from "vue"
import type {ComputedRef, Ref} from "vue"

/**
 * Client-side pagination state for an embedded `KsDataTable` whose rows are already
 * fully in memory (the parent renders the rows, the table only owns the pager UI).
 *
 * `currentPage` / `pageSize` are meant to be bound with `v-model:currentPage` /
 * `v-model:pageSize`; `paged` is what the parent must render. When the source list
 * shrinks below the current window (e.g. a search filter), the page resets to 1 so
 * the table never shows an empty page while rows exist. Page-size changes need no
 * handling here: `KsDataTable` already emits `update:currentPage(1)` on them.
 */
export function useClientPagination<T>(source: ComputedRef<T[]>): {
    currentPage: Ref<number>
    pageSize: Ref<number>
    paged: ComputedRef<T[]>
} {
    const currentPage = ref(1)
    const pageSize = ref(25)

    const paged = computed(() => {
        const start = (currentPage.value - 1) * pageSize.value
        return source.value.slice(start, start + pageSize.value)
    })

    watch(() => source.value.length, (length) => {
        const lastPage = Math.max(1, Math.ceil(length / pageSize.value))
        if (currentPage.value > lastPage) {
            currentPage.value = 1
        }
    })

    return {currentPage, pageSize, paged}
}
