<template>
    <DemoBlueprints v-if="tab === 'custom'" />
    <template v-else>
        <TopNavBar v-if="!embed" :title="routeInfo.title" />
        <section
            v-bind="$attrs"
            class="main-container"
            :class="{'blueprints-margin': !combinedView, 'detail-view': !!selectedBlueprintId}"
        >
            <BlueprintDetail
                v-if="selectedBlueprintId"
                :embed
                :combinedView
                :blueprintId="selectedBlueprintId"
                blueprintType="community"
                @back="selectedBlueprintId = undefined"
            />
            <BlueprintsBrowser
                :class="{'d-none': !!selectedBlueprintId}"
                :embed
                :blueprintKind="kind"
                blueprintType="community"
                @loaded="emit('loaded', $event)"
                @go-to-detail="(id: string) => (selectedBlueprintId = id)"
            />
        </section>
    </template>
</template>

<script setup lang="ts">
    import {computed, onBeforeUnmount, onMounted, ref, watch} from "vue"
    import {useI18n} from "vue-i18n"

    import TopNavBar from "../../../../components/layout/TopNavBar.vue"
    import BlueprintDetail from "override/components/flows/blueprints/BlueprintDetail.vue"
    import BlueprintsBrowser from "../../../../components/flows/blueprints/BlueprintsBrowser.vue"
    import DemoBlueprints from "../../../../components/demo/Blueprints.vue"

    import useRouteContext from "../../../../composables/useRouteContext"
    import {useRouteTabsStore} from "../../../../stores/routeTabs"

    defineOptions({inheritAttrs: false})

    const props = withDefaults(defineProps<{
        kind: "flow" | "dashboard" | "app";
        tab?: string;
        combinedView?: boolean;
        embed?: boolean;
    }>(), {
        tab: "community",
        combinedView: false,
        embed: false,
    })

    const emit = defineEmits<{loaded: [value: any]}>()

    const {t} = useI18n()
    const routeTabsStore = useRouteTabsStore()
    const tabsOwnerId = Symbol("blueprints-route-tabs")

    const selectedBlueprintId = ref<string>()

    const routeInfo = computed(() => ({
        title: props.kind === "flow"
            ? t("blueprints.flows")
            : props.kind === "dashboard"
                ? t("blueprints.dashboards")
                : t("blueprints.title"),
    }))

    useRouteContext(routeInfo)

    const blueprintTabs = computed(() => [
        {
            name: "custom",
            title: t("blueprints.custom"),
            route: {name: "blueprints", params: {kind: "flow", tab: "custom"}},
            locked: true,
        },
        {
            name: "flow-community",
            title: t("blueprints.flows"),
            route: {name: "blueprints", params: {kind: "flow", tab: "community"}},
        },
        {
            name: "dashboard-community",
            title: t("blueprints.dashboards"),
            route: {name: "blueprints", params: {kind: "dashboard", tab: "community"}},
        },
    ])

    function syncBlueprintTabs() {
        if (props.embed) return
        routeTabsStore.setTabs({
            ownerId: tabsOwnerId,
            tabs: blueprintTabs.value,
        })
    }

    onMounted(syncBlueprintTabs)
    watch(blueprintTabs, syncBlueprintTabs)
    onBeforeUnmount(() => routeTabsStore.clearTabsIfOwner(tabsOwnerId))
</script>

<style scoped lang="scss">
    .main-container {
        padding: var(--ks-spacing-6) !important;

        &:not(.blueprints-margin):not(.detail-view) {
            padding: 0 !important;
        }
    }
</style>