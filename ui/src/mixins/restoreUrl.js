export default {
    props: {
        restoreUrl: {
            type: Boolean,
            default: true
        },
    },
    created() {
        if (Object.keys(this.$route.query).length === 0) {
            this.loadInit = false;
            this.goToRestoreUrl();
        }
    },
    computed: {
        localStorageName() {
            return `${this.$route.name.replace("/", "_")}_restore_url`
        },

        localStorageValue() {
            if (window.sessionStorage.getItem(this.localStorageName)) {
                return JSON.parse(window.sessionStorage.getItem(this.localStorageName))
            } else {
                return null;
            }
        },
    },
    methods: {
        saveRestoreUrl() {
            if (!this.restoreUrl) {
                return;
            }

            window.sessionStorage.setItem(
                this.localStorageName,
                JSON.stringify(this.$route.query)
            );
        },
        goToRestoreUrl() {
            if (!this.restoreUrl) {
                return;
            }

            const localExist = this.localStorageValue !== null;

            const query = {...this.$route.query}
            const local = this.localStorageValue === null ? {} : {...this.localStorageValue};

            let change = false

            if (!localExist && this.isDefaultNamespaceAllow && localStorage.getItem("defaultNamespace")) {
                local["namespace"] = localStorage.getItem("defaultNamespace");
            }

            for (const key in local) {
                if (!query[key] && local[key]) {
                    query[key] = local[key]
                    change = true
                }
            }

            if (change) {
                this.$router.replace({query: query});
            } else {
                this.loadInit = true;
            }
        }
    }
}
