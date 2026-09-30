<template>
    <KsTooltip :content="props.tooltip ?? props.label">
        <KsTag type="primary" size="small" round class="badge" :label="props.label" />
    </KsTooltip>
</template>

<script setup lang="ts">
    interface Props {
        label: string;
        tooltip?: string;
    }
    const props = defineProps<Props>()
</script>

<style scoped lang="scss">
.badge {
    margin-left: var(--ks-spacing-2);
}
</style>