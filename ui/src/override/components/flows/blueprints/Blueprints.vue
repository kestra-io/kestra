<template>
    <DemoBlueprints v-if="tab === 'custom'" />
    <DemoDashboards v-else-if="kind === 'dashboard'" blueprints />
    <DemoApps v-else-if="kind === 'app'" blueprints />
    <BlueprintsPage
        v-else
        v-bind="$attrs"
        :kind
        :tabs="blueprintTabs"
        :combinedView
        :embed
        @loaded="emit('loaded')"
    />
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useI18n} from "vue-i18n"
    import BlueprintsPage from "../../../../components/flows/blueprints/BlueprintsPage.vue"
    import DemoBlueprints from "../../../../components/demo/Blueprints.vue"
    import DemoDashboards from "../../../../components/demo/Dashboards.vue"
    import DemoApps from "../../../../components/demo/Apps.vue"
    import type {BlueprintKind} from "../../../../stores/blueprints"

    defineOptions({inheritAttrs: false})

    withDefaults(defineProps<{
        kind: BlueprintKind;
        tab?: string;
        combinedView?: boolean;
        embed?: boolean;
    }>(), {
        tab: "community",
        combinedView: false,
        embed: false,
    })

    const emit = defineEmits<{loaded: []}>()

    const {t} = useI18n()

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
            locked: true,
        },
        {
            name: "app-community",
            title: t("blueprints.apps"),
            route: {name: "blueprints", params: {kind: "app", tab: "community"}},
            locked: true,
        },
    ])
</script>
