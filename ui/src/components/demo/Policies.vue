<template>
    <TopNavBar v-if="topbar" :title="routeInfo.title" />
    <Empty
        type="policies"
        demoCta
        :title="t(`demos.policies.title`)"
    >
        <template #description>
            {{ $t(`demos.policies.message`) }}
        </template>
    </Empty>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useI18n} from "vue-i18n"
    import useRouteContext from "../../composables/useRouteContext"

    import Empty from "../layout/empty/Empty.vue"
    import TopNavBar from "../../components/layout/TopNavBar.vue"

    defineProps({
        topbar: {
            type: Boolean,
            default: true,
        },
    })

    const {t} = useI18n()

    const routeInfo = computed(
        () => ({title: t("demos.policies.header")}),
    )

    useRouteContext(routeInfo)
</script>
