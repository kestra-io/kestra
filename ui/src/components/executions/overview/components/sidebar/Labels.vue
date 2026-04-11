<template>
    <ks-row v-for="(label, index) in props.labels" :key="index">
        <ks-col>
            <span>{{ label.key }}:</span>
            <ks-tag :title="label.value">
                {{ label.value }}
            </ks-tag>
        </ks-col>
    </ks-row>
</template>

<script setup lang="ts">
    const props = defineProps<{
        labels: { key: string; value: string }[];
    }>();
</script>

<style scoped lang="scss">

.kel-row {
    margin-bottom: calc(1rem / 1.5) !important;

    .kel-col {
        display: flex;
        align-items: center;
        gap: 6px;
        font-size: var(--kel-font-size-small);

        & :deep(.kel-tag) {
            margin-left: calc(1rem / 1.5);
            overflow: hidden;
            background-color: var(--ks-tag-background-active);
            color: var(--ks-tag-content);

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
