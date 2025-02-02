<template>
    <top-nav-bar v-if="!embed" :title="routeInfo.title" />
    <dotted-layout
        :embed="embed"
        :phrase="$t('blueprints.header.catch phrase.2', {kind})"
        :alt="$t('blueprints.header.alt')"
        :image="headerImage"
        :image-dark="headerImageDark"
    >
        <section class="main-container" v-bind="$attrs">
            <blueprint-detail
                v-if="selectedBlueprintId"
                :embed="embed"
                :blueprint-id="selectedBlueprintId"
                blueprint-type="community"
                @back="selectedBlueprintId = undefined"
            />
            <blueprints-browser
                @loaded="$emit('loaded', $event)"
                :class="{'d-none': !!selectedBlueprintId}"
                :embed="embed"
                :blueprint-kind="kind"
                blueprint-type="community"
                @go-to-detail="blueprintId => selectedBlueprintId = blueprintId"
            >
                <template #nav>
                    <tabs
                        :top="false"
                        @changed="tabChanged"
                        v-if="isFlow"
                        :embed-active-tab="embed ? embeddedTab : undefined"
                        :route-name="$route.name"
                        :tabs="tabs"
                        type="card"
                    />
                </template>
                <template v-if="embeddedTab === 'custom'" #content>
                    <DemoBlueprints />
                </template>
            </blueprints-browser>
        </section>
    </dotted-layout>
</template>
<script>
    import RouteContext from "../../../../mixins/routeContext";
    import TopNavBar from "../../../../components/layout/TopNavBar.vue";
    import DottedLayout from "../../../../components/layout/DottedLayout.vue";
    import BlueprintDetail from "../../../../components/flows/blueprints/BlueprintDetail.vue";
    import DemoBlueprints from "../../../../components/demo/Blueprints.vue";
    import Tabs from "../../../..//components/Tabs.vue";
    import BlueprintsBrowser from "./BlueprintsBrowser.vue";

    import headerImage from "../../../../assets/icons/blueprint.svg";
    import headerImageDark from "../../../../assets/icons/blueprint-dark.svg";

    export default {
        mixins: [RouteContext],
        inheritAttrs: false,
        components: {
            DottedLayout,
            BlueprintDetail,
            BlueprintsBrowser,
            TopNavBar,
            Tabs,
            DemoBlueprints
        },
        emits: [
            "loaded"
        ],
        props: {
            kind: {
                type: String,
                required: true
            },
            tab: {
                type: String,
                default: "community"
            }
        },
        data() {
            return {
                selectedBlueprintId: undefined,
                headerImage,
                headerImageDark,
                embeddedTab: "community"
            }
        },
        mounted(){
            if(!this.embed && !this.$route?.params?.tab) {
                this.$router.push({name: "blueprints", params: {tab: "community", kind: this.kind}})
            }
        },
        computed: {
            routeInfo() {
                return {
                    title: this.$t("blueprints.title")
                };
            },
            tabs() {
                return [
                    {
                        name: "community",
                        title: this.$t("blueprints.community"),
                        query: this.$route.query
                    },
                    {
                        name: "custom",
                        title: this.$t("blueprints.custom"),
                        query: this.$route.query,
                        locked: true
                    }
                ]
            },
            isFlow() {
                return this.kind === "flow";
            },
        },
        methods: {
            tabChanged(newTab) {
                if (!newTab?.name) {
                    return;
                }
                this.embeddedTab = newTab.name;
            },

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