<template>
    <DemoBlueprints v-if="props.tab === 'custom'" />
    <template v-else>
        <TopNavBar v-if="!props.embed" :title="routeInfo.title" />
        <DottedLayout
            :embed="props.embed"
            :phrase="$t('blueprints.header.catch phrase.2', {kind: props.kind})"
            :alt="$t('blueprints.header.alt')"
            :image="headerImage"
            :imageDark="headerImageDark"
        >
            <section :class="{'main-container': true, 'blueprints-margin': !props.combinedView}" v-bind="$attrs">
                <BlueprintDetail
                    v-if="selectedBlueprintId"
                    :embed="props.embed"
                    :blueprintId="selectedBlueprintId"
                    blueprintType="community"
                    @back="selectedBlueprintId = undefined"
                    :combinedView="props.combinedView"
                />
                <BlueprintsBrowser
                    @loaded="emit('loaded', $event)"
                    :class="{'d-none': !!selectedBlueprintId}"
                    :embed="props.embed"
                    :blueprintKind="props.kind"
                    blueprintType="community"
                    @go-to-detail="(blueprintId: string) => selectedBlueprintId = blueprintId"
                />
            </section>
        </DottedLayout>
    </template>
</template>
<script setup lang="ts">
    import {ref, computed} from "vue";
    import {useI18n} from "vue-i18n";
    import TopNavBar from "../../../../components/layout/TopNavBar.vue";
    import DottedLayout from "../../../../components/layout/DottedLayout.vue";
    import BlueprintDetail from "../../../../override/components/flows/blueprints/BlueprintDetail.vue";
    import BlueprintsBrowser from "../../../../override/components/flows/blueprints/BlueprintsBrowser.vue";
    import DemoBlueprints from "../../../../components/demo/Blueprints.vue";
    import useRouteContext from "../../../../composables/useRouteContext";

    import headerImage from "../../../../assets/icons/blueprint.svg";
    import headerImageDark from "../../../../assets/icons/blueprint-dark.svg";

    defineOptions({inheritAttrs: false});

    const {t} = useI18n();

    interface Props {
        kind: "flow" | "dashboard" | "app";
        tab?: string;
        combinedView?: boolean;
        embed?: boolean;
    }

    const props = withDefaults(defineProps<Props>(), {
        tab: "community",
        combinedView: false,
        embed: false
    });

    const emit = defineEmits<{loaded: [value: any]}>();

    const selectedBlueprintId = ref<string | undefined>(undefined);

    const routeInfo = computed(() => ({title: props.kind === "flow" ? t("blueprints.flows") :
        props.kind === "dashboard" ? t("blueprints.dashboards") :
        t("blueprints.title")
    }));

    useRouteContext(routeInfo);
</script>
<style scoped lang="scss">
    .main-container {
        padding: 32px !important;
    }
</style>