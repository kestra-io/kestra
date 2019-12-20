export default {
    created() {
        this.onLoad()
    },
    watch: {
        $route() {
            this.onLoad()
        }
    },
    methods: {
        onLoad() {
            this.$store.commit('layout/setTopNavbar', this.routeInfo)
        }
    }
}