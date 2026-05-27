<template>
    <div class="setting-row" :class="{'setting-row--stacked': stacked}">
        <div class="setting-row__info">
            <KsText tag="div" class="setting-row__label">{{ label }}</KsText>
            <KsText v-if="description" tag="div" size="small" class="setting-row__description">
                {{ description }}
            </KsText>
        </div>
        <div class="setting-row__control">
            <slot />
        </div>
    </div>
</template>

<script setup lang="ts">
    defineProps<{
        label: string
        description?: string
        stacked?: boolean
    }>()
</script>

<style scoped lang="scss">
    .setting-row {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 1rem;
        padding: 1rem;

        &:not(:last-child) {
            border-bottom: 1px solid var(--ks-border-default);
        }

        &__info {
            display: flex;
            flex-direction: column;
            align-items: flex-start;
            gap: 0.25rem;
            min-width: 0;
        }

        &__label {
            align-self: flex-start;
            font-weight: 600;
            color: var(--ks-text-primary);
        }

        &__description {
            align-self: flex-start;
            color: var(--ks-text-secondary);
        }

        &__control {
            flex-shrink: 0;
            display: flex;
            justify-content: flex-end;
            align-items: center;
            min-width: 0;
            max-width: 100%;
            overflow: hidden;
        }

        &--stacked {
            flex-direction: column;
            align-items: stretch;

            .setting-row__control {
                width: 100%;
                justify-content: flex-start;
            }
        }
    }
</style>
