import qb from "../utils/queryBuilder";
export default {
    created() {
        this.loadFilters()
        this.query = qb.build(this.$route, this.fields);
        this.loadData(this.onDataLoaded);
    },
    data() {
        return {
            query: "*",
            sort: "",
            ready: false
        };
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
            this.query = qb.build(this.$route, this.fields);
            this.loadData(this.onDataLoaded);
            this.saveFilters()
        },
        onSort(sortItem) {
            const sort = [
                `${sortItem.sortBy}:${sortItem.sortDesc ? "desc" : "asc"}`
            ];
            this.$router.push({
                query: {...this.$route.query, sort}
            });
            this.loadData(this.onDataLoaded);
            this.saveFilters()
        },
        onRowDoubleClick(item) {
            this.$router.push({name: this.dataType + "Edit", params: item});
        },
        onPageChanged(item) {
            this.$router.push({
                query: {
                    ...this.$route.query,
                    size: item.size,
                    page: item.page
                }
            });
            this.loadData(this.onDataLoaded);
            this.saveFilters()
        },
        onNamespaceSelect() {
            this.query = qb.build(this.$route, this.fields);
            this.$router.push({query: {...this.$route.query, page: 1}})
            this.loadData(this.onDataLoaded);
            this.saveFilters()
        },
        onDataLoaded () {
            this.ready = true
        },
        saveFilters() {
            if (this.isBasePage) {
                localStorage.setItem(
                    this.storageName,
                    JSON.stringify(this.$route.query)
                );
            }
        },
        loadFilters () {
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
    }
}
