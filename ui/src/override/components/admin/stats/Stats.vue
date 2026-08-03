<template>
    <TopNavBar :title="routeInfo.title" />
    <section class="container" v-show="ready">
        <div class="stats-content">
            <Usages @loaded="ready = true" />
            <Pricing />
        </div>
    </section>
</template>

<script setup lang="ts">
    import {ref, computed} from "vue"
    import TopNavBar from "../../../../components/layout/TopNavBar.vue"
    import Usages from "../../../../components/admin/stats/Usages.vue"
    import Pricing from "../../../../components/admin/stats/Pricing.vue"
    import useRouteContext from "../../../../composables/useRouteContext"
    import {useI18n} from "vue-i18n"

    const ready = ref(false)

    const {t} = useI18n()

    const routeInfo = computed(() => ({
        title: t("system overview"),
    }))
    useRouteContext(routeInfo)
</script>

<style scoped lang="scss">
    .container {
        margin-block: var(--ks-spacing-10);
    }

    .stats-content {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-6);
        max-width: 593px;
        margin-inline: auto;
    }
</style>