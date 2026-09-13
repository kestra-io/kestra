<template>
    <EnterpriseBadge enable class="locked" @click="router.push({name: 'dashboards/create'})">
        <slot>
            <KsButton :icon="ChartLineVariant">
                <span class="text-truncate">{{ $t("dashboards.default") }}</span>
            </KsButton>
        </slot>
    </EnterpriseBadge>
</template>

<script setup lang="ts">
    import {useRouter} from "vue-router"
    import ChartLineVariant from "vue-material-design-icons/ChartLineVariant.vue"
    import EnterpriseBadge from "../../../components/EnterpriseBadge.vue"

    // OSS stand-in for the EE dashboard selector: custom dashboards are an Enterprise
    // feature, so the trigger renders locked and leads to the Enterprise demo page.
    // Props and emits mirror the EE component so shared callers can bind either implementation.
    defineProps<{placement?: string}>()
    defineEmits<{dashboard: [id: string]}>()

    const router = useRouter()
</script>

<style scoped lang="scss">
    .locked {
        cursor: pointer;
    }
</style>
