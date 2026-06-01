import {defineComponent} from "vue"
import {defaultNamespace} from "../composables/useNamespaces"

/**
 * @deprecated Use `composables/useRestoreUrl.ts` instead.
 */
export default defineComponent({
    props: {
        restoreUrl: {
            type: Boolean,
            default: true,
        },
    },
    created() {
        if (Object.keys(this.$route.query).length === 0 && this.restoreUrl) {
            (this as unknown as {loadInit: boolean}).loadInit = false
            this.goToRestoreUrl()
        }
    },
    computed: {
        localStorageName(): string {
            const tenant = this.$route.params.tenant
            const routeName = typeof this.$route.name === "string" ? this.$route.name.replace("/", "_") : ""
            return `${routeName}${this.$route.params.tab ? "_" + this.$route.params.tab : ""}${tenant ? "_" + tenant : ""}_restore_url`
        },

        localStorageValue(): Record<string, unknown> | null {
            const stored = window.sessionStorage.getItem(this.localStorageName)
            if (stored) {
                return JSON.parse(stored) as Record<string, unknown>
            } else {
                return null
            }
        },
    },
    methods: {
        saveRestoreUrl() {
            if (!this.restoreUrl) {
                return
            }

            if (Object.keys(this.$route.query).length > 0 || (this.localStorageValue !== null && Object.keys(this.localStorageValue).length > 0)) {

                if (Object.keys(this.$route.query).length === 0) {
                    window.sessionStorage.removeItem(this.localStorageName)
                } else {
                    window.sessionStorage.setItem(
                        this.localStorageName,
                        JSON.stringify(this.$route.query),
                    )
                }
            }
        },
        goToRestoreUrl() {
            if (!this.restoreUrl) {
                return
            }

            const localExist = this.localStorageValue !== null

            const query: Record<string, unknown> = {...this.$route.query}
            const local: Record<string, unknown> = this.localStorageValue === null ? {} : {...this.localStorageValue}

            let change = false

            if (!localExist && (this as unknown as {isDefaultNamespaceAllow?: boolean}).isDefaultNamespaceAllow && defaultNamespace()) {
                local["namespace"] = defaultNamespace()
            }

            for (const key in local) {
                if (!query[key] && local[key]) {
                    // empty array break the application
                    if (local[key] instanceof Array && (local[key] as unknown[]).length === 0) {
                        continue
                    }

                    query[key] = local[key]
                    change = true
                }
            }

            if (change) {
                // wait for the router to be ready
                this.$nextTick(() => {
                    this.$router.replace({query: query as Record<string, string>})
                })
            } else {
                (this as unknown as {loadInit: boolean}).loadInit = true
            }
        },
    },
})
