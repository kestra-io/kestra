<template>
    <dl class="ks-topology-details">
        <div
            v-for="row in rows"
            :key="row.label"
            class="ks-topology-details__row"
        >
            <dt class="ks-topology-details__label">{{ row.label }}</dt>
            <dd class="ks-topology-details__value" :title="row.value">{{ row.value }}</dd>
        </div>
    </dl>
</template>

<script setup lang="ts">
    defineOptions({name: "KsTopologyDetails"})

    defineProps<{
        rows: {label: string; value: string}[];
    }>()
</script>

<style lang="scss" scoped>
    .ks-topology-details {
        margin: 0;
        display: flex;
        flex-direction: column;
        font-size: var(--ks-font-size-xs);

        &__row {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: var(--ks-spacing-4);
            padding: var(--ks-spacing-2) var(--ks-spacing-3);
            border-bottom: 1px solid var(--ks-border-subtle);

            &:last-child {
                border-bottom: none;
            }
        }

        &__label {
            color: var(--ks-text-secondary);
            white-space: nowrap;
            font-weight: var(--ks-font-weight-regular);
        }

        &__value {
            margin: 0;
            color: var(--ks-text-primary);
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }
    }
</style>
