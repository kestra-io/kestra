<template>
    <blueprints-page-header v-if="!embed" />
    <div class="main-container" v-bind="$attrs">
        <blueprint-detail v-if="selectedBlueprintId" :embed="embed" :blueprint-id="selectedBlueprintId" @back="selectedBlueprintId = undefined" />
        <blueprints-browser @loaded="$emit('loaded', $event)" :class="{'d-none': !!selectedBlueprintId}" :embed="embed" :blueprint-base-uri="blueprintUri" @go-to-detail="blueprintId => selectedBlueprintId = blueprintId" />
    </div>
</template>
<script>
    import RouteContext from "../../../../mixins/routeContext";
    import SearchField from "../../../../components/layout/SearchField.vue";
    import DataTable from "../../../../components/layout/DataTable.vue";
    import BlueprintDetail from "../../../../components/flows/blueprints/BlueprintDetail.vue";
    import BlueprintsBrowser from "./BlueprintsBrowser.vue";
    import BlueprintsPageHeader from "./BlueprintsPageHeader.vue";
    import {apiUrl} from "override/utils/route";

    export default {
        mixins: [RouteContext],
        inheritAttrs: false,
        components: {
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
            },
            blueprintUri() {
                return `${apiUrl(this.$store)}/blueprints/community`
            }
        }
    };
</script>
<style scoped lang="scss">
    .main-container {
        padding-top: 6px !important;
    }
</style>