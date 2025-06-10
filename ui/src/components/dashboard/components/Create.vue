<template>
    <TopNavBar :title="routeInfo.title" :breadcrumb="routeInfo.breadcrumb" />
    <section class="full-container">
        <dashboard-editor
            v-if="initialSource"
            allow-save-unchanged
            @save="save"
            :initial-source
        />
    </section>
</template>

<script>
    import RouteContext from "../../../mixins/routeContext";
    import TopNavBar from "../../../components/layout/TopNavBar.vue";
    import DashboardEditor from "./Editor.vue";

    import YAML_MAIN from "../assets/default_main_definition.yaml?raw";

    export default {
        mixins: [RouteContext],
        components: {
            DashboardEditor,
            TopNavBar,
        },
        data() {
            return {
                initialSource: undefined,
            };
        },
        async beforeMount() {
            const blueprintId = this.$route.query.blueprintId;

            this.initialSource = blueprintId
                ? await this.$store.dispatch("blueprints/getBlueprintSource", {
                    type: "community",
                    kind: "dashboard",
                    id: blueprintId,
                })
                : YAML_MAIN;
        },
        methods: {
            async save(input) {
                const dashboard = await this.$store.dispatch(
                    "dashboard/create",
                    input,
                );

                this.$toast().success(
                    this.$t("dashboards.creation.confirmation", {
                        title: dashboard.title,
                    }),
                );

                this.$store.dispatch("core/isUnsaved", false);
                this.$router.push({
                    name: "home",
                    params: {id: dashboard.id},
                    query: {created: true},
                });
            },
        },
        computed: {
            routeInfo() {
                return {
                    title: this.$t("dashboards.labels.singular"),
                    breadcrumb: [{label: this.$t("dashboards.creation.label"), link: {}}],
                };
            },
        },
    };
</script>
