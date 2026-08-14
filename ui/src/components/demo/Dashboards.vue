<template>
    <TopNavBar v-if="topbar" :title="routeInfo.title" />
    <Empty
        type="dashboards"
        demoCta
        :title="t(`${keyPrefix}.title`)"
    >
        <template #description>
            {{ t(`${keyPrefix}.message`) }}
        </template>
    </Empty>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useI18n} from "vue-i18n"
    import useRouteContext from "../../composables/useRouteContext"

    const props = defineProps({
        topbar: {
            type: Boolean,
            default: true,
        },
        // Blueprint-flavored copy for the locked dashboard-blueprints tab.
        blueprints: {
            type: Boolean,
            default: false,
        },
    })

    import Empty from "../layout/empty/Empty.vue"
    import TopNavBar from "../../components/layout/TopNavBar.vue"

    const {t} = useI18n()

    const keyPrefix = computed(() => props.blueprints ? "demos.dashboards.blueprints" : "demos.dashboards")

    const routeInfo = computed(() => ({title: props.blueprints ? t("blueprints.dashboards") : t("demos.dashboards.header")}))

    useRouteContext(routeInfo)
</script>
