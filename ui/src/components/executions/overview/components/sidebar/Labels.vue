<template>
    <KsRow v-for="(label, index) in props.labels" :key="index">
        <KsCol>
            <span>{{ label.key }}:</span>
            <KsTag :title="label.value">
                {{ label.value }}
            </KsTag>
        </KsCol>
    </KsRow>
</template>

<script setup lang="ts">
    const props = defineProps<{
        labels: { key: string; value: string }[];
    }>()
</script>

<style scoped lang="scss">

.kel-row {
    margin-bottom: calc(1rem / 1.5) !important;

    .kel-col {
        display: flex;
        align-items: center;
        gap: 6px;
        font-size: var(--ks-font-size-sm);

        & :deep(.kel-tag) {
            margin-left: calc(1rem / 1.5);
            overflow: hidden;
            background-color: var(--ks-bg-tag-active);
            color: var(--ks-text-primary);

            & .kel-tag__content {
                display: block;
                min-width: 0;
                white-space: nowrap;
                overflow: hidden;
                text-overflow: ellipsis;
            }
        }
    }
}
</style>
