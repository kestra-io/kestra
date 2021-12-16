import _merge from "lodash/merge";
import _cloneDeep from "lodash/cloneDeep";

export default {
    created() {
        this.internalPageSize = this.pageSize;
        this.internalPageNumber = this.pageNumber;

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
            internalPageSize: undefined,
            internalPageNumber: undefined,
        };
    },
    props: {
        filters: {
            type: Object,
            default: () => {}
        },
        pageSize: {
            type: Number,
            default: 25
        },
        pageNumber: {
            type: Number,
            default: 1
        },
    },
    watch: {
        $route(to, from) {
            if (to.query !== from.query) {
                this.load(this.onDataLoaded);
            }
        }
    },
    methods: {
        onSort(sortItem) {
            if (!this.embed && sortItem && sortItem.sortBy) {
                const sort = [
                    `${sortItem.sortBy}:${sortItem.sortDesc ? "desc" : "asc"}`
                ];
                this.$router.push({
                    query: {...this.$route.query, sort}
                });
            }
        },
        onRowDoubleClick(item) {
            this.$router.push({
                name: this.dblClickRouteName || this.$route.name.replace("/list", "/update"),
                params: item
            });
        },
        onDataTableValue(keyOrObject, value) {
            const values = typeof (keyOrObject) === "string" ? {[keyOrObject]: value} : keyOrObject;
            let query = {...this.$route.query};

            for (const [key, value] of Object.entries(values)) {
                if (value === undefined || value === "" || value === null) {
                    delete query[key]
                } else {
                    query[key] = value;
                }
            }

            delete query.page;
            this.internalPageNumber = 1

            this.$router.push({query: query})
        },
        onPageChanged(item) {
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
                this.load();
            }
        },
        queryWithFilter() {
            return _merge(_cloneDeep(this.$route.query), this.filters || {});
        },
        load(callback) {
            if (this.$refs.dataTable) {
                this.$refs.dataTable.isLoading = true;
            }

            this.loadData(callback || this.onDataLoaded);
        },
        onDataLoaded () {
            this.ready = true
            this.loadInit = true;


            if (this.saveRestoreUrl) {
                this.saveRestoreUrl()
            }

            if (this.$refs.dataTable) {
                this.$refs.dataTable.isLoading = false;
            }
        },
    }
}
