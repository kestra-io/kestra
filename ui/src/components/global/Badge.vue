<template>
    <KsTooltip :content="props.tooltip ?? props.label">
        <KsButton type="primary" size="small" class="badge">
            {{ props.label }}
        </KsButton>
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
    --kel-button-border-color: var(--ks-button-content-primary);

    margin-left: 0.5rem;
    border-color: var(--ks-button-content-primary);
    border-radius: calc(var(--kel-border-radius-round) * 2);

    &:hover {
        border-color: var(--ks-button-content-primary);
    }
}
</style>
