<template>
    <TopNavBar :title="routeInfo.title" />
    <Empty
        type="apps"
        demoCta
        :title="t(`${keyPrefix}.title`)"
    >
        <template #description>
            {{ $t(`${keyPrefix}.message`) }}
        </template>
    </Empty>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useI18n} from "vue-i18n"
    import Empty from "../layout/empty/Empty.vue"
    import TopNavBar from "../../components/layout/TopNavBar.vue"
    import useRouteContext from "../../composables/useRouteContext"

    const props = defineProps({
        blueprints: {
            type: Boolean,
            default: false,
        },
    })

    const {t} = useI18n()

    const keyPrefix = computed(() =>
        props.blueprints ? "demos.apps.blueprints" : "demos.apps",
    )

    const routeInfo = computed(() =>
        ({title: props.blueprints ? t("blueprints.apps") : t("demos.apps.title")}),
    )

    useRouteContext(routeInfo)
</script>