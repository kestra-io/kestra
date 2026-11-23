<template>
    <TopNavBar v-if="!embed" :title="routeInfo.title">
        <template v-if="$slots.actions" #actions>
            <slot name="actions" />
        </template>
    </TopNavBar>
    <section
        v-bind="$attrs"
        class="main-container"
        :class="{
            'blueprints-margin': !combinedView,
            'detail-view': !!selectedBlueprintId,
            'full-container': !embed,
        }"
    >
        <BlueprintDetail
            v-if="selectedBlueprintId"
            :embed
            :combinedView
            :blueprintId="selectedBlueprintId"
            :blueprintType
            :kind
            @back="selectedBlueprintId = undefined"
        />
        <BlueprintsBrowser
            ref="browser"
            :class="{'is-hidden': !!selectedBlueprintId}"
            :embed
            :blueprintKind="kind"
            :blueprintType
            :tagsResponseMapper
            @loaded="emit('loaded')"
            @go-to-detail="(id: string) => (selectedBlueprintId = id)"
        >
            <template v-if="$slots['beside-search']" #beside-search>
                <slot name="beside-search" />
            </template>
            <template v-if="$slots.empty" #empty>
                <slot name="empty" />
            </template>
            <template v-if="$slots.buttons" #buttons="{blueprint}">
                <slot name="buttons" :blueprint />
            </template>
        </BlueprintsBrowser>
    </section>
</template>

<script setup lang="ts">
    import {computed, onBeforeUnmount, onMounted, ref, useTemplateRef, watch} from "vue"
    import {useI18n} from "vue-i18n"
    import TopNavBar from "../../layout/TopNavBar.vue"
    import BlueprintDetail from "override/components/flows/blueprints/BlueprintDetail.vue"
    import BlueprintsBrowser from "./BlueprintsBrowser.vue"
    import useRouteContext from "../../../composables/useRouteContext"
    import {useRouteTabsStore, type RouteTab} from "../../../stores/routeTabs"
    import type {BlueprintKind, BlueprintTag, BlueprintType} from "../../../stores/blueprints"

    defineOptions({inheritAttrs: false})

    const props = withDefaults(defineProps<{
        kind: BlueprintKind;
        tabs: RouteTab[];
        blueprintType?: BlueprintType;
        combinedView?: boolean;
        embed?: boolean;
        tagsResponseMapper?: (tagsResponse: BlueprintTag[]) => Record<string, BlueprintTag>;
    }>(), {
        blueprintType: "community",
        combinedView: false,
        embed: false,
    })

    const emit = defineEmits<{loaded: []}>()

    const {t} = useI18n()
    const routeTabsStore = useRouteTabsStore()
    const tabsOwnerId = Symbol("blueprints-route-tabs")
    const browser = useTemplateRef("browser")

    const selectedBlueprintId = ref<string>()

    const routeInfo = computed(() => ({
        title: t(props.blueprintType === "custom"
            ? "blueprints.custom"
            : {
                flow: "blueprints.flows",
                dashboard: "blueprints.dashboards",
                app: "blueprints.apps",
            }[props.kind]),
    }))

    useRouteContext(routeInfo, props.embed)

    function syncTabs() {
        if (props.embed) return
        routeTabsStore.setTabs({ownerId: tabsOwnerId, tabs: props.tabs})
    }

    onMounted(syncTabs)
    watch(() => props.tabs, syncTabs)
    onBeforeUnmount(() => routeTabsStore.clearTabsIfOwner(tabsOwnerId))

    defineExpose({reload: () => browser.value?.reload()})
</script>

<style scoped>
    .main-container {
        padding: var(--ks-spacing-6) !important;

        &:not(.blueprints-margin):not(.detail-view) {
            padding: 0 !important;
        }
    }

    .is-hidden {
        display: none;
    }
</style>
