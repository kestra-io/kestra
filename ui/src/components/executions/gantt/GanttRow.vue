<template>
    <div
        class="gantt-row"
        :class="{'is-selected': selected, 'is-compact': compact}"
        role="row"
        tabindex="0"
        :aria-level="depth + 1"
        :aria-selected="selected"
        :aria-expanded="expandable ? expanded : undefined"
        :data-row-key="nodeKey"
        @click="emit('select')"
        @keydown.enter.self.prevent="emit('select')"
        @keydown.space.self.prevent="emit('select')"
    >
        <div class="row-label" role="gridcell" data-test="gantt-task-label">
            <span class="guides" aria-hidden="true">
                <span
                    v-for="(ancestor, level) in guides"
                    :key="level"
                    class="guide"
                    :class="{'is-lineage': ancestor.key === highlightedKey}"
                />
            </span>
            <button
                v-if="expandable"
                type="button"
                class="chevron"
                data-test="gantt-row-chevron"
                :aria-label="$t(expanded ? 'gantt_row_collapse' : 'gantt_row_expand', {name: label})"
                :aria-expanded="expanded"
                @click.stop="emit('toggle')"
                @keydown.enter.stop
                @keydown.space.stop
            >
                <KsIcon size="sm">
                    <ChevronDown v-if="expanded" />
                    <ChevronRight v-else />
                </KsIcon>
            </button>
            <span v-else class="chevron-spacer" />
            <slot name="label" />
        </div>
        <div v-if="!compact" class="row-offset" role="gridcell" data-test="gantt-task-offset">
            {{ offset }}
        </div>
        <div class="row-bar" role="gridcell">
            <div class="row-bar-fill" data-test="gantt-task-bar" :style="barStyle" />
        </div>
        <div class="row-duration" role="gridcell">
            <slot name="duration" />
        </div>
        <div class="row-state" role="gridcell">
            <KsExecutionStatus :status="state" />
        </div>
        <div class="row-actions" role="gridcell" @click.stop @keydown.stop>
            <slot name="actions" />
        </div>
        <div v-if="$slots.failure" class="row-failure" role="gridcell">
            <slot name="failure" />
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import ChevronRight from "vue-material-design-icons/ChevronRight.vue"
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue"
    import {KsExecutionStatus, KsIcon} from "@kestra-io/design-system"
    import {useGanttTree, type Ancestor} from "./ganttTree"

    const props = withDefaults(defineProps<{
        nodeKey: string;
        label: string;
        ancestors: Ancestor[];
        offset: string;
        barStyle: Record<string, string>;
        state: string;
        selected: boolean;
        expandable?: boolean;
        expanded?: boolean;
    }>(), {
        expandable: false,
        expanded: false,
    })

    const emit = defineEmits<{
        select: [];
        toggle: [];
    }>()

    const {compact, highlightedKey} = useGanttTree()

    const depth = computed(() => props.ancestors.length)
    const guides = computed(() => props.ancestors)
</script>

<style scoped>
    .gantt-row {
        --gantt-indent: var(--ks-spacing-5);

        position: relative;
        display: flex;
        align-items: center;
        min-height: 2.75rem;
        padding: 0 var(--ks-spacing-8) 0 var(--ks-spacing-2);
        background: var(--ks-dropdown-bg);
        border-top: var(--ks-border-width-thin) solid var(--ks-border-default);
        color: var(--ks-icon-muted);
        cursor: pointer;

        &:hover {
            background: var(--ks-bg-hover-elevated);
        }

        &.is-compact {
            --gantt-indent: var(--ks-spacing-4);
        }

        &.is-selected {
            background: var(--ks-bg-tag-hover);
            box-shadow: inset var(--ks-spacing-1) 0 0 var(--ks-border-focus);
        }

        &:focus-visible {
            outline: var(--ks-border-width-base) solid var(--ks-border-focus);
            outline-offset: calc(-1 * var(--ks-spacing-px) * 2);
        }
    }

    .row-label {
        --gantt-label-gap: var(--ks-spacing-2);

        flex: 1 1 0;
        min-width: 6rem;
        overflow-x: clip;
        align-self: stretch;
        display: flex;
        align-items: center;
        gap: var(--gantt-label-gap);

        code {
            color: var(--ks-text-primary);
        }
    }

    .guides {
        display: flex;
        align-self: stretch;
        flex-shrink: 0;
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
            left: calc(50% + var(--gantt-label-gap));
            width: var(--ks-border-width-thin);
            background: var(--ks-border-strong);
        }

        &.is-lineage::before {
            left: calc(50% + var(--gantt-label-gap) - var(--ks-border-width-thin) / 2);
            width: var(--ks-border-width-base);
            background: var(--ks-border-focus);
        }
    }

    .chevron,
    .chevron-spacer {
        flex-shrink: 0;
        width: var(--gantt-indent);
        height: var(--gantt-indent);
        margin-right: calc(-1 * var(--ks-spacing-1));
    }

    .chevron {
        display: grid;
        place-items: center;
        padding: 0;
        border: 0;
        border-radius: var(--ks-radius-xs);
        background: transparent;
        color: var(--ks-icon-muted);
        cursor: pointer;

        &:hover {
            background: var(--ks-bg-active);
            color: var(--ks-icon-default);
        }

        &:focus-visible {
            outline: var(--ks-border-width-base) solid var(--ks-border-focus);
        }
    }

    .row-offset {
        flex: 0 0 var(--gantt-offset-width);
        padding: 0 var(--ks-spacing-1);
        text-align: end;
        white-space: nowrap;
        font-family: var(--ks-font-family-mono);
        font-size: var(--ks-font-size-xs);
        color: var(--ks-text-secondary);
    }

    .row-bar {
        flex: 0 0 var(--gantt-bar-width);
        position: relative;
        height: var(--ks-spacing-2);
        margin: 0 var(--ks-spacing-1);
        border-radius: var(--ks-spacing-1);
        background: var(--ks-bg-active);
    }

    .row-bar-fill {
        position: absolute;
        top: 0;
        bottom: 0;
        border-radius: var(--ks-spacing-1);
    }

    .row-duration {
        flex: 0 0 var(--gantt-duration-width);
        min-width: 0;
        padding: 0 var(--ks-spacing-1);
        overflow: hidden;
        white-space: nowrap;
        font-family: var(--ks-font-family-mono);
        font-size: var(--ks-font-size-xs);
        color: var(--ks-text-primary);
    }

    .row-state {
        flex: 0 0 var(--gantt-state-width);
        padding: 0 var(--ks-spacing-1);
    }

    .row-actions {
        position: absolute;
        right: var(--ks-spacing-2);
        top: 50%;
        transform: translateY(-50%);
    }

    .gantt-row.is-compact {
        display: grid;
        grid-template-columns: minmax(0, 1fr) auto auto auto;
        grid-template-areas:
            "label duration state actions"
            "bar bar bar bar"
            "failure failure failure failure";
        gap: var(--ks-spacing-2) var(--ks-spacing-3);
        padding: var(--ks-spacing-3);

        & > * {
            padding: 0;
        }

        .row-label {
            grid-area: label;
            min-width: 0;
        }

        .row-duration {
            grid-area: duration;
        }

        .row-state {
            grid-area: state;
        }

        .row-actions {
            position: static;
            grid-area: actions;
            transform: none;
        }

        .row-bar {
            grid-area: bar;
            margin: 0;
        }

        .row-failure {
            grid-area: failure;
        }
    }
</style>
