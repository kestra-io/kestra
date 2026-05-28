<template>
    <KsTooltip
        v-if="count !== undefined"
        :content="tooltip"
        placement="bottom"
        :showAfter="500"
    >
        <button
            type="button"
            class="stat"
            :aria-label="tooltip"
            @click="navigate"
        >
            <component :is="icon" class="stat-icon" />
            <span class="stat-count">{{ count }}</span>
            <span class="stat-label">{{ suffix }}</span>
        </button>
    </KsTooltip>
</template>

<script setup lang="ts">
    import type {Component} from "vue"
    import {useRoute, useRouter} from "vue-router"

    const props = defineProps<{
        icon: Component
        count: number | undefined
        suffix: string
        tooltip: string
        tab: string
    }>()

    const route = useRoute()
    const router = useRouter()

    function navigate() {
        router.push({
            name: "flows/update",
            params: {...route.params, tab: props.tab},
            query: {...route.query},
        })
    }
</script>

<style scoped lang="scss">
    .stat {
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        padding: var(--ks-spacing-1) var(--ks-spacing-2);
        background: transparent;
        border: 1px solid transparent;
        border-radius: var(--ks-radius-base);
        font-size: var(--ks-font-size-sm);
        color: var(--ks-text-secondary);
        white-space: nowrap;
        cursor: pointer;
        transition: background-color 0.2s ease-in-out, color 0.2s ease-in-out;

        &:hover {
            background-color: var(--ks-background-card);
            color: var(--ks-text-primary);
        }

        &:focus-visible {
            outline: 2px solid var(--ks-border-active);
            outline-offset: 1px;
        }
    }

    .stat-icon {
        font-size: 1.1em;
        line-height: 0;
        flex-shrink: 0;
        color: var(--ks-icon-muted);
    }

    @media (max-width: 1600px) {
        .stat-label {
            display: none;
        }
    }

    @media (max-width: 1260px) {
        .stat-count {
            display: none;
        }

        .stat {
            padding: var(--ks-spacing-2);
            gap: 0;
            aspect-ratio: 1 / 1;
        }
    }
</style>
