<template>
    <top-nav-bar :title="routeInfo.title" :breadcrumb="routeInfo.breadcrumb" />
    <section class="full-container">
        <dashboard-editor
            @save="save"
            v-if="dashboardSource"
            :initial-source="dashboardSource"
        />
    </section>
</template>

<script>
    import RouteContext from "../../../mixins/routeContext";
    import TopNavBar from "../../../components/layout/TopNavBar.vue";
    import DashboardEditor from "./DashboardEditor.vue";

    export default {
        mixins: [RouteContext],
        components: {
            DashboardEditor,
            TopNavBar,
        },
        methods: {
            async save(input) {
                await this.$store.dispatch("dashboard/update", {
                    id: this.$route.params.id,
                    source: input,
                });
                this.$store.dispatch("core/isUnsaved", false);
            },
        },
        data() {
            return {
                dashboardSource: undefined,
            };
        },
        beforeMount() {
            this.$store
                .dispatch("dashboard/load", this.$route.params.id)
                .then((dashboard) => {
                    this.dashboardSource = dashboard.sourceCode;
                });
        },
        computed: {
            routeInfo() {
                return {
                    title: this.$route.params.id,
                    breadcrumb: [
                        {
                            label: this.$t("custom_dashboard"),
                            link: {},
                        },
                    ],
                };
            },
        },
    };
</script>
