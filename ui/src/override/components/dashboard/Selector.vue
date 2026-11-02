<template>
    <KsButton :icon="ChartLineVariant" @click="router.push({name: 'dashboards/create'})">
        <KsText truncated>{{ $t("dashboards.default") }}</KsText>
        <LockOutline class="lock" />
    </KsButton>
</template>

<script setup lang="ts">
    import {useRouter} from "vue-router"
    import ChartLineVariant from "vue-material-design-icons/ChartLineVariant.vue"
    import LockOutline from "vue-material-design-icons/LockOutline.vue"

    /** OSS stand-in for the EE selector: renders locked and leads to the Enterprise demo page.
     *  Its emits mirror the EE component so shared callers can bind either one. */
    defineEmits<{dashboard: [id: string]}>()

    const router = useRouter()
</script>

<style scoped>
    .lock {
        display: inline-flex;
        margin-left: var(--ks-spacing-1);
        color: var(--ks-text-secondary);
    }
</style>
