<template>
    <top-nav-bar v-if="!embed" :title="$t('blueprints.title')" />
    <blueprints-page-header v-if="!embed" class="ms-0 mw-100"/>
    <div :class="{'mt-3': !embed}" class="main-container" v-bind="$attrs">
        <blueprint-detail v-if="selectedBlueprintId" :embed="embed" :blueprint-id="selectedBlueprintId" @back="selectedBlueprintId = undefined" />
        <blueprints-browser @loaded="$emit('loaded', $event)" :class="{'d-none': !!selectedBlueprintId}" :embed="embed" :blueprint-base-uri="blueprintUri" @go-to-detail="blueprintId => selectedBlueprintId = blueprintId" />
    </div>
</template>
<script>
    import TopNavBar from "../../../../components/layout/TopNavBar.vue";
    import SearchField from "../../../../components/layout/SearchField.vue";
    import DataTable from "../../../../components/layout/DataTable.vue";
    import BlueprintDetail from "../../../../components/flows/blueprints/BlueprintDetail.vue";
    import BlueprintsBrowser from "./BlueprintsBrowser.vue";
    import BlueprintsPageHeader from "./BlueprintsPageHeader.vue";
    import {apiUrl} from "override/utils/route";

    export default {
        inheritAttrs: false,
        components: {
            SearchField,
            DataTable,
            BlueprintDetail,
            BlueprintsBrowser,
            BlueprintsPageHeader,
            TopNavBar
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