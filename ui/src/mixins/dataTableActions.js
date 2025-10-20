import _merge from "lodash/merge";
import _cloneDeep from "lodash/cloneDeep";
import _isEqual from "lodash/isEqual";

export default {
    created() {
        this.refreshPaging();

        // @TODO: ugly hack from restoreUrl
        if (this.loadInit) {
            this.load(this.onDataLoaded);
        }
    },
    data() {
        return {
            sort: "",
            dblClickRouteName: undefined,
            loadInit: true,
            ready: false,
            internalPageSize: 25,
            internalPageNumber: 1,
            internalSort: undefined
        };
    },
    props: {
        filters: {
            type: Object,
            default: () => {
            }
        },
        pageSize: {
            type: Number
        },
        pageNumber: {
            type: Number
        }
    },
    watch: {
        $route(newValue, oldValue) {
            if (oldValue.name === newValue.name && !_isEqual(newValue.query, oldValue.query)) {
                this.refreshPaging();
                this.load(this.onDataLoaded);
            }
        }
    },
    methods: {
        sortString(sortItem, sortKeyMapper) {
            if (sortItem && sortItem.prop && sortItem.order) {
                return `${sortKeyMapper(sortItem.prop)}:${sortItem.order === "descending" ? "desc" : "asc"}`;
            }
        },
        onSort(sortItem, sortKeyMapper = (k) => k) {
            this.internalSort = this.sortString(sortItem, sortKeyMapper);

            if (this.internalSort) {
                const sort = this.internalSort;
                this.$router.push({
                    query: {...this.$route.query, sort}
                });
            } else {
                this.load(this.onDataLoaded);
            }
        },
        onRowDoubleClick(item) {
            this.$router.push({
                name: this.dblClickRouteName || this.$route.name.replace("/list", "/update"),
                params: {
                    ...item,
                    tenant: this.$route.params.tenant
                }
            });
        },
        onDataTableValue(keyOrObject, value) {
            const values = typeof (keyOrObject) === "string" ? {[keyOrObject]: value} : keyOrObject;
            let query = {...this.$route.query};

            for (const [key, value] of Object.entries(values)) {
                if (value === undefined || value === "" || value === null || value.length === 0) {
                    delete query[key];
                } else {
                    query[key] = value;
                }
            }

            this.internalPageNumber = 1;

            this.$router.push({query: query});
        },
        onPageChanged(item) {
            if (this.internalPageSize === item.size && this.internalPageNumber === item.page) return;

            this.internalPageSize = item.size;
            this.internalPageNumber = item.page;

            if (!this.embed) {
                this.$router.push({
                    query: {
                        ...this.$route.query,
                        size: item.size,
                        page: item.page
                    }
                });
            } else {
                this.load(this.onDataLoaded);
            }
        },
        queryWithFilter(namespace, excludedKeys = []) {
            let query = this.$route.query;

            if (namespace !== undefined) {
                query = Object.fromEntries(
                    Object.entries(query)
                        .filter(([key]) => key.startsWith(`${namespace}[`))
                        .map(([key, value]) => [key.substring(namespace.length + 2, key.length - 1), value])
                );
            }

            if (excludedKeys.length > 0) {
                const filterKeyMatcher = new RegExp(`^(?:filters\\[)?(?:${excludedKeys.join(")|(?:")})`);
                query = Object.fromEntries(
                    Object.entries(query).filter(([key]) => filterKeyMatcher.exec(key) === null)
                );
            }

            return _merge(_cloneDeep(
                query,
                this.filters || {}
            ));
        },
        load(callback) {
            if (this.$refs.dataTable) {
                this.$refs.dataTable.isLoading = true;
            }

            this.loadData(callback || this.onDataLoaded);
        },
        onDataLoaded() {
            this.ready = true;
            this.loadInit = true;

            if (this.saveRestoreUrl) {
                this.saveRestoreUrl();
            }

            if (this.$refs.dataTable) {
                this.$refs.dataTable.isLoading = false;
            }
        },
        refreshPaging() {
            this.internalPageSize = this.pageSize ?? this.$route.query.size ?? 25;
            this.internalPageNumber = this.pageNumber ?? this.$route.query.page ?? 1;
        }
    }
};
