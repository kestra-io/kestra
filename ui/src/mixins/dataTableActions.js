import queryBuilder from "../utils/queryBuilder";
import { CardPlugin } from "bootstrap-vue";
export default {
    created() {
        if (localStorage.getItem(this.storageName)) {
            if (localStorage.getItem(this.storageName) != JSON.stringify(this.$route.query)) {
                this.$router.push({
                    query: JSON.parse(localStorage.getItem(this.storageName))
                });
            }
        }
        this.query = queryBuilder(this.$route, this.fields);
        this.loadData(this.onDataLoaded);
    },
    watch: {
        $route() {
            localStorage.setItem(
                this.storageName,
                JSON.stringify(this.$route.query)
            );
        }
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
                title: this.$t(this.dataType + 's')
            };
        },
        storageName() {
            return `${this.dataType}Queries`
        },
        searchableFields() {
            return this.fields.filter(f => f.sortable);
        },
    },
    methods: {
        onSearch() {
            this.query = queryBuilder(this.$route, this.fields);
            this.loadData(this.onDataLoaded);
        },
        onSort(sortItem) {
            const sort = [
                `${sortItem.sortBy}:${sortItem.sortDesc ? "desc" : "asc"}`
            ];
            this.$router.push({
                query: { ...this.$route.query, sort }
            });
            this.loadData(this.onDataLoaded);
        },
        onRowDoubleClick(item) {            
            this.$router.push({ name: this.dataType, params: item });
        },
        onNamespaceSelect() {
            this.query = queryBuilder(this.$route, this.fields);
            this.loadData(this.onDataLoaded);
        },
        onDataLoaded () {
            this.ready = true
        }
    }

}