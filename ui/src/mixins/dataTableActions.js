import qb from "../utils/queryBuilder";
import State from "../utils/state";
export default {
    created() {
        this.loadFilters()
        this.query = qb.build(this.$route, this.filters);
        this.loadData(this.onDataLoaded);
    },
    data() {
        return {
            query: "*",
            sort: "",
            ready: false,
        };
    },
    props: {
        filters: {
            type: Object,
            default: () => {}
        },
    },
    computed: {
        routeInfo() {
            return {
                title: this.$t(this.dataType + "s")
            };
        },
        storageName() {
            return `${this.dataType}Queries`
        },
        searchableFields() {
            return this.fields.filter(f => f.sortable);
        },
        isBasePage() {
            return ["executionsList", "flowsList"].includes(this.$route.name)
        }
    },
    methods: {
        onSearch() {
            this.query = qb.build(this.$route, this.filters);
            this.loadData(this.onDataLoaded);
            this.saveFilters()
        },
        onSort(sortItem) {
            if (!this.embed) {
                const sort = [
                    `${sortItem.sortBy}:${sortItem.sortDesc ? "desc" : "asc"}`
                ];
                this.$router.push({
                    query: {...this.$route.query, sort}
                });
            }

            this.loadData(this.onDataLoaded);
            this.saveFilters()
        },
        onRowDoubleClick(item) {
            this.$router.push({name: this.dataType + "Edit", params: item});
        },
        onPageChanged(item) {
            if (!this.embed) {
                this.$router.push({
                    query: {
                        ...this.$route.query,
                        size: item.size,
                        page: item.page
                    }
                });
                this.saveFilters()
            }

            this.loadData(this.onDataLoaded);
        },
        onNamespaceSelect() {
            if (!this.embed) {
                this.query = qb.build(this.$route, this.filters);
                this.$router.push({query: {...this.$route.query, page: 1}})
            }

            this.loadData(this.onDataLoaded);
            this.saveFilters()
        },
        onDataLoaded () {
            this.ready = true
        },
        saveFilters() {
            if (this.isBasePage && !this.embed) {
                localStorage.setItem(
                    this.storageName,
                    JSON.stringify(this.$route.query)
                );
            }
        },
        loadFilters () {
            if (!this.embed) {
                const query = {...this.$route.query}
                let change = false
                if (this.isBasePage) {
                    const userPreferences = JSON.parse(localStorage.getItem(this.storageName) || "{}")
                    for (const key in userPreferences) {
                        if (!query[key] && userPreferences[key]) {
                            query[key] = userPreferences[key]
                            change = true
                        }
                    }
                }

                if (change) {
                    this.$router.push({query: query});
                }
            }
        },
        isRunning(item){
            return State.isRunning(item.state.current);
        }
    }
}
