<template>
    <div class="message-row" role="row" :aria-level="ancestors.length + 1">
        <div class="message-cell" role="gridcell">
            <span class="guides" aria-hidden="true">
                <span v-for="level in ancestors.length" :key="level" class="guide" />
            </span>
            <span class="indent-spacer" />
            <slot />
        </div>
    </div>
</template>

<script setup lang="ts">
    import type {Ancestor} from "./ganttTree"

    defineProps<{
        ancestors: Ancestor[];
    }>()
</script>

<style scoped>
    .message-row {
        --gantt-indent: var(--ks-spacing-5);

        min-height: 2.5rem;
        padding: 0 var(--ks-spacing-2);
        background: var(--ks-bg-base);
        border-top: var(--ks-border-width-thin) solid var(--ks-border-default);
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-xs);
    }

    .message-cell {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-3);
        min-height: inherit;
    }

    .guides {
        display: flex;
        align-self: stretch;
        margin: calc(-1 * var(--ks-border-width-thin)) 0;
    }

    .guide {
        position: relative;
        width: var(--gantt-indent);

        &::before {
            content: "";
            position: absolute;
            top: 0;
            bottom: 0;
            left: 50%;
            width: var(--ks-border-width-thin);
            background: var(--ks-border-strong);
        }
    }

    .indent-spacer {
        width: var(--gantt-indent);
    }
</style>
