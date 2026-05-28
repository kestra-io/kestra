<template>
    <TopNavBar :title="routeInfo.title" v-if="!isFullScreen()" />
    <Empty
        type="tenants"
        demoCta
        :title="$t('demos.tenants.title')"
        video="https://www.youtube.com/embed/z4uzAyjKeoc"
        learnMore="https://kestra.io/docs/enterprise/governance/tenants"
    >
        <template #description>
            {{ $t('demos.tenants.message') }}
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

    const routeInfo = computed(() => ({title: t("demos.tenants.title")}))

    useRouteContext(routeInfo)

    function isFullScreen() {
        return document.getElementsByTagName("html")[0].classList.contains("full-screen")
    }
</script>