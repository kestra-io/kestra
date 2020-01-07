export default {
    props: {
        preventRouteInfo : {
            type: Boolean,
            default: false
        }
    },
    mounted() {
        this.onLoad()
    },
    watch: {
        $route() {
            this.onLoad()
        }
    },
    methods: {
        onLoad() {
            if (!this.preventRouteInfo) {
                this.$store.commit('layout/setTopNavbar', this.routeInfo)
            }
        }
    }
}