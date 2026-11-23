<template>
    <div
        ref="root"
        class="node-details-peek nowheel"
        data-test="node-details-peek"
        role="dialog"
        aria-modal="false"
        :aria-label="ariaLabel"
        :style="{left: `${x}px`, top: `${y}px`, width: `${width}px`}"
        @mouseenter="emit('peekEnter')"
        @mouseleave="emit('peekLeave')"
    >
        <header class="node-details-peek-header">
            <span class="node-details-peek-title" data-test="node-details-peek-title">{{ title }}</span>
            <span v-if="type" class="node-details-peek-type">{{ type }}</span>
        </header>
        <div class="node-details-peek-body" data-test="node-details-peek-body">
            <slot />
        </div>
    </div>
</template>

<script lang="ts" setup>
    import {onBeforeUnmount, onMounted, ref} from "vue"

    defineProps<{
        x: number;
        y: number;
        width: number;
        title: string;
        type?: string;
        ariaLabel: string;
    }>()

    const emit = defineEmits<{
        (event: "peekEnter"): void;
        (event: "peekLeave"): void;
        (event: "measured", height: number): void;
    }>()

    defineOptions({name: "NodeDetailsPeek"})

    const root = ref<HTMLElement | undefined>(undefined)
    let observer: ResizeObserver | undefined

    onMounted(() => {
        if (!root.value || typeof ResizeObserver === "undefined") return
        observer = new ResizeObserver(() => {
            if (root.value) emit("measured", root.value.offsetHeight)
        })
        observer.observe(root.value)
    })

    onBeforeUnmount(() => observer?.disconnect())
</script>

<style scoped>
    .node-details-peek {
        position: absolute;
        box-sizing: border-box;
        max-height: 20rem;
        overflow: auto;
        pointer-events: auto;
        background: var(--ks-bg-surface);
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-base);
        box-shadow: 0 0.5rem 1rem var(--ks-shadow-elevated);
    }

    .node-details-peek:has(.node-details-peek-body:empty) {
        display: none;
    }

    .node-details-peek-header {
        display: flex;
        align-items: baseline;
        justify-content: space-between;
        gap: var(--ks-spacing-2);
        padding: var(--ks-spacing-2) var(--ks-spacing-3);
        border-bottom: 1px solid var(--ks-border-default);
    }

    .node-details-peek-title {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        font-size: var(--ks-font-size-sm);
        font-weight: var(--ks-font-weight-semibold);
        color: var(--ks-text-primary);
    }

    .node-details-peek-type {
        flex-shrink: 0;
        font-size: var(--ks-font-size-xs);
        color: var(--ks-text-secondary);
    }
</style>
