<template>
    <top-nav-bar v-if="!embed" :title="routeInfo.title" />
    <dotted-layout
        :embed="embed"
        :phrase="$t('blueprints.header.catch phrase.2')"
        :alt="$t('blueprints.header.alt')"
        :image="headerImage"
        :image-dark="headerImageDark"
    >
        <section class="main-container" v-bind="$attrs">
            <blueprint-detail v-if="selectedBlueprintId" :embed="embed" :blueprint-id="selectedBlueprintId" @back="selectedBlueprintId = undefined" :blueprint-base-uri="blueprintUri" />
            <blueprints-browser @loaded="$emit('loaded', $event)" :class="{'d-none': !!selectedBlueprintId}" :embed="embed" :blueprint-base-uri="blueprintUri" @go-to-detail="blueprintId => selectedBlueprintId = blueprintId" />
        </section>
    </dotted-layout>
</template>
<script>
    import RouteContext from "../../../../mixins/routeContext";
    import TopNavBar from "../../../../components/layout/TopNavBar.vue";
    import DottedLayout from "../../../../components/layout/DottedLayout.vue";
    import BlueprintDetail from "../../../../components/flows/blueprints/BlueprintDetail.vue";
    import BlueprintsBrowser from "./BlueprintsBrowser.vue";
    import {apiUrl} from "override/utils/route";

    import headerImage from "../../../../assets/icons/blueprint.svg";
    import headerImageDark from "../../../../assets/icons/blueprint-dark.svg";

    export default {
        mixins: [RouteContext],
        inheritAttrs: false,
        components: {
            DottedLayout,
            BlueprintDetail,
            BlueprintsBrowser,
            TopNavBar
        },
        emits: [
            "loaded"
        ],
        data() {
            return {
                selectedBlueprintId: undefined,
                headerImage,
                headerImageDark
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
        margin: 0 32px;
    }
</style>