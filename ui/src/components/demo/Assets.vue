<template>
    <TopNavBar v-if="topbar" :title="routeInfo.title" />
    <Empty
        type="assets"
        demoCta
        :title="t(`demos.assets.title`)"
    >
        <template #description>
            {{ $t(`demos.assets.message`) }}
        </template>
    </Empty>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useI18n} from "vue-i18n"
    import useRouteContext from "../../composables/useRouteContext"

    defineProps({
        topbar: {
            type: Boolean,
            default: true,
        },
    })

    import Empty from "../layout/empty/Empty.vue"
    import TopNavBar from "../../components/layout/TopNavBar.vue"

    const {t} = useI18n()

    const routeInfo = computed(() => ({title: t("demos.assets.header")}))

    useRouteContext(routeInfo)
</script>