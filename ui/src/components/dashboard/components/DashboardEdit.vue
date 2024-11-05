<template>
    <top-nav-bar :title="routeInfo.title" />
    <section class="full-container">
        <dashboard-editor @save="save" v-if="dashboardSource" :initial-source="dashboardSource" />
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
            TopNavBar
        },
        props: {
            id: {
                type: String,
                required: true
            }
        },
        methods: {
            async save(input) {
                await this.$store.dispatch("dashboard/update", {
                    id: this.id,
                    source: input
                });
                this.$store.dispatch("core/isUnsaved", false);
            }
        },
        data() {
            return {
                dashboardSource: undefined
            }
        },
        beforeMount() {
            this.$store.dispatch("dashboard/load", this.id).then(dashboard => {
                this.dashboardSource = dashboard.sourceCode;
            });
        },
        computed: {
            routeInfo() {
                return {
                    title: this.id,
                    breadcrumb: [
                        {
                            label: this.$t(`${this.dataType}s`),
                            link: {
                                name: `${this.dataType}s/list`,
                            }
                        }
                    ]
                };
            }
        }
    };
</script>
