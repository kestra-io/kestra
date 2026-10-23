<template>
    <EnterpriseBadge enable class="locked" @click="router.push({name: 'dashboards/create'})">
        <KsButton :icon="ChartLineVariant">
            <KsText truncated>{{ $t("dashboards.default") }}</KsText>
        </KsButton>
    </EnterpriseBadge>
</template>

<script setup lang="ts">
    import {useRouter} from "vue-router"
    import ChartLineVariant from "vue-material-design-icons/ChartLineVariant.vue"
    import EnterpriseBadge from "../../../components/EnterpriseBadge.vue"

    /** OSS stand-in for the EE selector: renders locked and leads to the Enterprise demo page.
     *  Its emits mirror the EE component so shared callers can bind either one. */
    defineEmits<{dashboard: [id: string]}>()

    const router = useRouter()
</script>

<style scoped>
    .locked {
        cursor: pointer;
    }
</style>
