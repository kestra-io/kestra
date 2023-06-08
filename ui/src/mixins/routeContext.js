export default {
    props: {
        topNavbar: {
            type: Boolean,
            default: true
        },
        preventRouteInfo: {
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
                this.$store.commit("layout/setTopNavbar", this.topNavbar ? this.routeInfo : undefined);

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
