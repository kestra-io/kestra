import {defineComponent} from "vue"
import _merge from "lodash/merge"
import _cloneDeep from "lodash/cloneDeep"
import _isEqual from "lodash/isEqual"

interface SortItem {
    prop?: string
    order?: string
}

export default defineComponent({
    created() {
        this.refreshPaging()

        // @TODO: ugly hack from restoreUrl
        if (this.loadInit) {
            this.load((this as unknown as {onDataLoaded: () => void}).onDataLoaded)
        }
    },
    data() {
        return {
            sort: "",
            dblClickRouteName: undefined as string | undefined,
            loadInit: true,
            ready: false,
            internalPageSize: 25,
            internalPageNumber: 1,
            internalSort: undefined as string | undefined,
        }
    },
    props: {
        filters: {
            type: Object,
            default: () => {
            },
        },
        pageSize: {
            type: Number,
        },
        pageNumber: {
            type: Number,
        },
    },
    watch: {
        $route(newValue: {name: string; query: unknown}, oldValue: {name: string; query: unknown}) {
            if (oldValue.name === newValue.name && !_isEqual(newValue.query, oldValue.query)) {
                this.refreshPaging()
                this.load((this as unknown as {onDataLoaded: () => void}).onDataLoaded)
            }
        },
    },
    methods: {
        sortString(sortItem: SortItem, sortKeyMapper: (key: string) => string): string | undefined {
            if (sortItem && sortItem.prop && sortItem.order) {
                return `${sortKeyMapper(sortItem.prop)}:${sortItem.order === "descending" ? "desc" : "asc"}`
            }
        },
        onSort(sortItem: SortItem, sortKeyMapper: (key: string) => string = (k) => k) {
            this.internalSort = this.sortString(sortItem, sortKeyMapper)

            if (this.internalSort) {
                const sort = this.internalSort
                this.$router.push({
                    query: {...this.$route.query, sort},
                })
            } else {
                this.load((this as unknown as {onDataLoaded: () => void}).onDataLoaded)
            }
        },
        onRowDoubleClick(item: Record<string, unknown>) {
            this.$router.push({
                name: this.dblClickRouteName || (this.$route.name as string).replace("/list", "/update"),
                params: {
                    ...item,
                    tenant: this.$route.params.tenant,
                },
            })
        },
        onDataTableValue(keyOrObject: string | Record<string, unknown>, value?: unknown) {
            const values = typeof (keyOrObject) === "string" ? {[keyOrObject]: value} : keyOrObject
            const query: Record<string, unknown> = {...this.$route.query}

            for (const [key, entryValue] of Object.entries(values)) {
                if (entryValue === undefined || entryValue === "" || entryValue === null || (Array.isArray(entryValue) && entryValue.length === 0)) {
                    delete query[key]
                } else {
                    query[key] = entryValue
                }
            }

            this.internalPageNumber = 1

            this.$router.push({query: query as Record<string, string>})
        },
        onPageChanged(item: {size: number; page: number}) {
            if (this.internalPageSize === item.size && this.internalPageNumber === item.page) return

            this.internalPageSize = item.size
            this.internalPageNumber = item.page

            if (!(this as unknown as {embed?: boolean}).embed) {
                this.$router.push({
                    query: {
                        ...this.$route.query,
                        size: item.size,
                        page: item.page,
                    },
                })
            } else {
                this.load((this as unknown as {onDataLoaded: () => void}).onDataLoaded)
            }
        },
        queryWithFilter(namespace?: string, excludedKeys: string[] = []): Record<string, unknown> {
            let query: Record<string, unknown> = this.$route.query as Record<string, unknown>

            if (namespace !== undefined) {
                query = Object.fromEntries(
                    Object.entries(query)
                        .filter(([key]) => key.startsWith(`${namespace}[`))
                        .map(([key, value]) => [key.substring(namespace.length + 2, key.length - 1), value]),
                )
            }

            if (excludedKeys.length > 0) {
                const filterKeyMatcher = new RegExp(`^(?:filters\\[)?(?:${excludedKeys.join(")|(?:")})`)
                query = Object.fromEntries(
                    Object.entries(query).filter(([key]) => filterKeyMatcher.exec(key) === null),
                )
            }

            return _merge(_cloneDeep(query), this.filters || {})
        },
        load(callback?: () => void) {
            const dataTable = (this.$refs as Record<string, {isLoading?: boolean} | undefined>).dataTable
            if (dataTable) {
                dataTable.isLoading = true
            }

            ;(this as unknown as {loadData: (cb: (() => void) | undefined) => void}).loadData(callback || (this as unknown as {onDataLoaded: () => void}).onDataLoaded)
        },
        onDataLoaded() {
            this.ready = true
            this.loadInit = true

            const self = this as unknown as {saveRestoreUrl?: () => void}
            if (self.saveRestoreUrl) {
                self.saveRestoreUrl()
            }

            const dataTable = (this.$refs as Record<string, {isLoading?: boolean} | undefined>).dataTable
            if (dataTable) {
                dataTable.isLoading = false
            }
        },
        refreshPaging() {
            this.internalPageSize = this.pageSize ?? (Number(this.$route.query.size) || 25)
            this.internalPageNumber = this.pageNumber ?? (Number(this.$route.query.page) || 1)
        },
    },
})
