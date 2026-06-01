import {defineComponent} from "vue"

/**
 * @deprecated Use `composables/useRouteContext.ts` instead.
 */
export default defineComponent({
    props: {
        embed: {
            type: Boolean,
            default: false,
        },
    },
    mounted() {
        this.handleTitle()
    },
    watch: {
        $route() {
            this.handleTitle()
        },
    },
    methods: {
        handleTitle() {
            if(!this.embed) {
                let baseTitle

                if (document.title.lastIndexOf("|") > 0) {
                    baseTitle = document.title.substring(document.title.lastIndexOf("|") + 1)
                } else {
                    baseTitle = document.title
                }

                document.title = (this as unknown as {routeInfo: {title: string}}).routeInfo.title + " | " + baseTitle
            }
        },
    },
})
