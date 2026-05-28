<template>
    <TopNavBar :title="routeInfo.title" />
    <Empty
        type="apps"
        demoCta
        :title="t(`demos.apps.title`)"
        video="https://www.youtube.com/embed/KwBO8mcS3kk"
        learnMore="https://kestra.io/docs/enterprise/scalability/apps"
    >
        <template #description>
            {{ $t(`demos.apps.message`) }}
        </template>
    </Empty>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useI18n} from "vue-i18n"
    import Empty from "../layout/empty/Empty.vue"
    import TopNavBar from "../../components/layout/TopNavBar.vue"
    import useRouteContext from "../../composables/useRouteContext"

    const {t} = useI18n()

    const routeInfo = computed(() => ({title: t("demos.apps.title")}))

    useRouteContext(routeInfo)
</script>