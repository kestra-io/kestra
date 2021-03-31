export default {
    props: {
        restoreUrl: {
            type: Boolean,
            default: true
        },
    },
    created() {
        if (Object.keys(this.$route.query).length === 0 &&
            Object.keys(this.localStorage).length > 0
        ) {
            this.loadInit = false;
            this.goToRestoreUrl();
        }
    },
    computed: {
        localStorageName() {
            return `${this.$route.name.replace("/", "_")}_restore_url`
        },

        localStorage() {
            return JSON.parse(localStorage.getItem(this.localStorageName) || "{}")
        },
    },
    methods: {
        saveRestoreUrl() {
            if (!this.restoreUrl) {
                return;
            }

            localStorage.setItem(
                this.localStorageName,
                JSON.stringify(this.$route.query)
            );
        },
        goToRestoreUrl() {
            if (!this.restoreUrl) {
                return;
            }

            const query = {...this.$route.query}

            let change = false

            for (const key in this.localStorage) {
                if (!query[key] && this.localStorage[key]) {
                    query[key] = this.localStorage[key]
                    change = true
                }
            }

            if (change) {
                this.$router.replace({query: query});
            }
        }
    }
}
