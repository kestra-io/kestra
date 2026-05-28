<template>
    <TopNavBar :title="routeInfo.title" />
    <Empty
        type="tests"
        demoCta
        :title="t(`demos.tests.title`)"
        video="https://www.youtube.com/embed/jMZ9Cs3xxpo"
        learnMore="https://kestra.io/docs/enterprise/governance/unit-tests"
    >
        <template #description>
            {{ $t(`demos.tests.message`) }}
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

    const routeInfo = computed(() => ({title: t("demos.tests.header")}))

    useRouteContext(routeInfo)
</script>