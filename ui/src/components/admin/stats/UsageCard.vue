<template>
    <component :is="to ? 'router-link' : 'div'" :to="to" class="card">
        <span class="label">{{ label }}</span>
        <span class="value">{{ value }}</span>
        <span v-if="subtitle" class="subtitle">{{ subtitle }}</span>
    </component>
</template>

<script setup lang="ts">
    import type {RouteLocationRaw} from "vue-router"

    defineProps<{
        label: string;
        value: string | number;
        subtitle?: string;
        to?: RouteLocationRaw;
    }>()
</script>

<style scoped lang="scss">
    .card {
        box-sizing: border-box;
        display: flex;
        flex-direction: column;
        align-items: flex-start;
        gap: var(--ks-spacing-1);
        min-height: 94px;
        padding: var(--ks-spacing-4);
        border: 1px solid var(--ks-border-default);
        border-radius: 8px;
        background: var(--ks-bg-surface);
        box-shadow: 0px 1px 4px 0px var(--ks-shadow-element);
        color: var(--ks-text-primary);
        line-height: 1.2;
        text-decoration: none;
        overflow: hidden;

        &:is(a) {
            transition: background-color 0.15s ease, border-color 0.15s ease;

            &:hover {
                background: var(--ks-bg-hover);
                border-color: var(--ks-border-focus);
            }
        }

        .label {
            font-size: var(--ks-font-size-xs);
        }

        .value {
            font-size: var(--ks-font-size-3xl);
            font-weight: var(--ks-font-weight-bold);
        }

        .subtitle {
            font-size: var(--ks-font-size-2xs);
            color: var(--ks-text-secondary);
        }
    }
</style>
