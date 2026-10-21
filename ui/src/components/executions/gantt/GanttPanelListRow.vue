<template>
    <button type="button" class="list-row" role="listitem">
        <slot name="leading" />
        <code class="list-name" :class="{'is-emphasized': emphasized}">{{ name }}</code>
        <span class="list-meta">{{ offset }}</span>
        <span class="list-meta"><Duration :histories="histories" /></span>
        <KsExecutionStatus :status="state" />
    </button>
</template>

<script setup lang="ts">
    import {Duration} from "@kestra-io/topology"
    import {KsExecutionStatus} from "@kestra-io/design-system"
    import type {StateHistory} from "../ganttTimeline"

    defineProps<{
        name: string;
        offset: string;
        histories: StateHistory[];
        state: string;
        emphasized?: boolean;
    }>()
</script>

<style scoped>
    .list-row {
        display: grid;
        grid-template-columns: auto minmax(0, 1fr) auto auto auto;
        align-items: center;
        gap: var(--ks-spacing-2);
        width: 100%;
        min-height: 2.5rem;
        padding: var(--ks-spacing-1) var(--ks-spacing-2);
        border: 0;
        border-top: var(--ks-border-width-thin) solid var(--ks-border-default);
        background: var(--ks-dropdown-bg);
        text-align: left;
        cursor: pointer;

        &:first-child {
            border-top: 0;
        }

        &:hover {
            background: var(--ks-bg-hover-elevated);
        }

        &:focus-visible {
            outline: var(--ks-border-width-base) solid var(--ks-border-focus);
            outline-offset: calc(-1 * var(--ks-border-width-base));
        }
    }

    .list-name {
        min-width: 0;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        font-size: var(--ks-font-size-sm);
        color: var(--ks-text-primary);

        &.is-emphasized {
            font-weight: var(--ks-font-weight-semibold);
        }
    }

    .list-meta {
        font-family: var(--ks-font-family-mono);
        font-size: var(--ks-font-size-2xs);
        color: var(--ks-text-secondary);
        white-space: nowrap;
    }
</style>
