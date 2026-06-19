<template>
    <TopNavBar :title="routeInfo.title" v-if="!isFullScreen() && !embed" />
    <Empty
        type="quotas"
        demoCta
        :title="$t('demos.quotas.title')"
        learnMore="https://kestra.io/docs/enterprise/governance/quotas"
    >
        <template #description>
            {{ $t('demos.quotas.message') }}
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

    defineProps({
        embed: {
            type:Boolean,
            default: false,
        },
    })

    defineOptions({
        name: "QuotasDemo",
        inheritAttrs: false,
    })

    const routeInfo = computed(() => ({title: t("demos.quotas.title")}))

    useRouteContext(routeInfo)

    function isFullScreen() {
        return document.getElementsByTagName("html")[0].classList.contains("full-screen")
    }
</script>
