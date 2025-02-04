<template>
    <top-nav-bar :title="routeInfo.title" :breadcrumb="routeInfo.breadcrumb" />
    <section class="full-container">
        <dashboard-editor
            @save="save"
            v-if="item && item.sourceCode"
            :initial-source="item.sourceCode"
        />
    </section>
</template>

<script>
    import RouteContext from "../../../../mixins/routeContext.js";
    import TopNavBar from "../../../../components/layout/TopNavBar.vue";
    import DashboardEditor from "../../../../components/dashboard/components/DashboardEditor.vue";

    export default {
        mixins: [RouteContext],
        components: {
            DashboardEditor,
            TopNavBar,
        },
        methods: {
            async save(input) {
                const response = await this.$store.dispatch("dashboard/update", {
                    id: this.$route.params.id,
                    source: input,
                });

                this.$toast().saved(response.title);

                this.$store.dispatch("core/isUnsaved", false);
            },
        },
        data() {
            return {
                item: undefined,
            };
        },
        beforeMount() {
            this.$store
                .dispatch("dashboard/load", this.$route.params.id)
                .then((dashboard) => {
                    this.item = dashboard;
                });
        },
        computed: {
            routeInfo() {
                const id = this.$route.params.id;

                return {
                    title:  this.item?.title || id,
                    breadcrumb: [
                        {
                            label: this.$t("custom_dashboard"),
                            link: {
                                name: "home",
                                params: {
                                    id: id
                                }
                            },
                        },
                    ],
                };
            },
        },
    };
</script>
