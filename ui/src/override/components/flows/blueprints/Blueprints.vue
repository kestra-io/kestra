<template>
    <top-nav-bar v-if="!embed" :title="routeInfo.title" />
    <blueprints-layout>
        <blueprints-page-header v-if="!embed" />
        <section :class="{'container': !embed}" class="main-container" v-bind="$attrs">
            <blueprint-detail v-if="selectedBlueprintId" :embed="embed" :blueprint-id="selectedBlueprintId" @back="selectedBlueprintId = undefined" :blueprint-base-uri="blueprintUri" />
            <blueprints-browser @loaded="$emit('loaded', $event)" :class="{'d-none': !!selectedBlueprintId}" :embed="embed" :blueprint-base-uri="blueprintUri" @go-to-detail="blueprintId => selectedBlueprintId = blueprintId" />
        </section>
    </blueprints-layout>
</template>
<script>
    import RouteContext from "../../../../mixins/routeContext";
    import TopNavBar from "../../../../components/layout/TopNavBar.vue";
    import BlueprintDetail from "../../../../components/flows/blueprints/BlueprintDetail.vue";
    import BlueprintsBrowser from "./BlueprintsBrowser.vue";
    import BlueprintsPageHeader from "./BlueprintsPageHeader.vue";
    import {apiUrl} from "override/utils/route";
    import BlueprintsLayout from "./BlueprintsLayout.vue";

    export default {
        mixins: [RouteContext],
        inheritAttrs: false,
        components: {
            BlueprintsLayout,
            BlueprintDetail,
            BlueprintsBrowser,
            BlueprintsPageHeader,
            TopNavBar
        },
        emits: [
            "loaded"
        ],
        data() {
            return {
                selectedBlueprintId: undefined
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
        padding-bottom: 4rem;
    }
</style>