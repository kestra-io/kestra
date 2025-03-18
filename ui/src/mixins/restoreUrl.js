export default {
    props: {
        restoreUrl: {
            type: Boolean,
            default: true
        },
    },
    created() {
        if (Object.keys(this.$route.query).length === 0 && this.restoreUrl) {
            this.loadInit = false;
            this.goToRestoreUrl();
        }
    },
    computed: {
        localStorageName() {
            const tenant = this.$route.params.tenant;
            return `${this.$route.name?.replace("/", "_")}${tenant ? "_" + tenant : ""}_restore_url`
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

            if (Object.keys(this.$route.query).length > 0 || (this.localStorageValue !== null && Object.keys(this.localStorageValue).length > 0)) {

                if (Object.keys(this.$route.query).length === 0) {
                    window.sessionStorage.removeItem(this.localStorageName);
                } else {
                    window.sessionStorage.setItem(
                        this.localStorageName,
                        JSON.stringify(this.$route.query)
                    );
                }
            }
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
                    // empty array break the application
                    if (local[key] instanceof Array && local[key].length === 0) {
                        continue;
                    }

                    query[key] = local[key]
                    change = true
                }
            }

            if (change) {
                // wait for the router to be ready
                this.$nextTick(() => {
                    this.$router.replace({query: query});
                });
            } else {
                this.loadInit = true;
            }
        }
    }
}
