<template>
    <blueprints-page-header v-if="!embed" />
    <div class="main-container" v-bind="$attrs">
        <blueprint-detail v-if="selectedBlueprintId" :embed="embed" :blueprint-id="selectedBlueprintId" @back="selectedBlueprintId = undefined" />
        <blueprints-browser v-else :embed="embed" blueprint-base-uri="/api/v1/blueprints/community" @go-to-detail="blueprintId => selectedBlueprintId = blueprintId"/>
    </div>
</template>
<script>
    import RouteContext from "../../../../mixins/routeContext";
    import RestoreUrl from "../../../../mixins/restoreUrl";
    import SearchField from "../../../../components/layout/SearchField.vue";
    import DataTable from "../../../../components/layout/DataTable.vue";
    import BlueprintDetail from "../../../../components/flows/blueprints/BlueprintDetail.vue";
    import BlueprintsBrowser from "./BlueprintsBrowser.vue";
    import BlueprintsPageHeader from "./BlueprintsPageHeader.vue";
    import TaskIcon from "../../../../components/plugins/TaskIcon.vue";

    export default {
        mixins: [RouteContext, RestoreUrl],
        inheritAttrs: false,
        components: {
            TaskIcon,
            SearchField,
            DataTable,
            BlueprintDetail,
            BlueprintsBrowser,
            BlueprintsPageHeader
        },
        data() {
            return {
                selectedBlueprintId: undefined
            }
        },
        async created() {
            this.selectedTag = this.$route?.query?.selectedTag ?? 0;
        },
        props: {
            embed: {
                type: Boolean,
                default: false
            }
        },
        computed: {
            routeInfo() {
                return {
                    title: this.$t("blueprints.title")
                };
            }
        }
    };
</script>
<style scoped lang="scss">
    .main-container {
        padding-top: 6px !important;
    }
</style>