export default {
    props: {
        preventRouteInfo : {
            type: Boolean,
            default: false
        }
    },
    mounted() {
        this.handleBreadcrumb()
    },
    watch: {
        $route() {
            this.handleBreadcrumb()
        }
    },
    methods: {
        handleBreadcrumb() {
            if (!this.preventRouteInfo) {
                this.$store.commit("layout/setTopNavbar", this.routeInfo)

                let baseTitle;

                if (document.title.lastIndexOf("|") > 0) {
                    baseTitle = document.title.substring(document.title.lastIndexOf("|") + 1);
                } else {
                    baseTitle = document.title;
                }

                document.title = this.routeInfo.title + " | " + baseTitle;
            }
        }
    }
}
