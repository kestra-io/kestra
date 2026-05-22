<template>
    <TopNavBar :title="routeInfo.title" />
    <Empty
        type="blueprints"
        demoCta
        :title="t(`demos.blueprints.title`)"
        video="https://www.youtube.com/embed/qbGfK-FJi6s?si=UTeK3V5Cj8FRHH91"
        learnMore="https://kestra.io/docs/enterprise/governance/custom-blueprints"
    >
        <template #description>
            {{ $t(`demos.blueprints.message`) }}
        </template>
    </Empty>
</template>

<script setup lang="ts">
    import Empty from "../layout/empty/Empty.vue"
    import {computed} from "vue"
    import TopNavBar from "../../components/layout/TopNavBar.vue"

    import {useI18n} from "vue-i18n"
    import useRouteContext from "../../composables/useRouteContext"

    const {t} = useI18n()

    const routeInfo = computed(() => ({title: t("demos.blueprints.title")}))

    useRouteContext(routeInfo)
</script>
