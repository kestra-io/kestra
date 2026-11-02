<template>
    <div class="setting-row" :class="{'setting-row--stacked': stacked}">
        <div class="setting-row__info">
            <KsText tag="div" class="setting-row__label">
                <span v-if="required" class="setting-row__required">*</span>{{ label }}
            </KsText>
            <KsText v-if="description || $slots.description" tag="div" size="small" class="setting-row__description">
                <slot name="description">{{ description }}</slot>
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
        required?: boolean
    }>()
</script>

<style scoped>
    .setting-row {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--ks-spacing-4);
        padding: var(--ks-spacing-4);

        &:not(:last-child) {
            border-bottom: 1px solid var(--ks-border-default);
        }
    }

    .setting-row__info {
        display: flex;
        flex-direction: column;
        align-items: flex-start;
        gap: var(--ks-spacing-1);
        min-width: 0;
    }

    .setting-row__label {
        align-self: flex-start;
        font-weight: 600;
        color: var(--ks-text-primary);
    }

    .setting-row__required {
        margin-right: var(--ks-spacing-1);
        color: var(--ks-text-error);
    }

    .setting-row__description {
        align-self: flex-start;
        color: var(--ks-text-secondary);
    }

    .setting-row__control {
        flex-shrink: 0;
        display: flex;
        justify-content: flex-end;
        align-items: center;
        min-width: 0;
        max-width: 100%;
        overflow: hidden;
    }

    .setting-row--stacked {
        flex-direction: column;
        align-items: stretch;

        .setting-row__control {
            width: 100%;
            justify-content: flex-start;
        }
    }
</style>
