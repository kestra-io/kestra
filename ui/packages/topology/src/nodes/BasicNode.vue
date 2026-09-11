<template>
    <div
        class="node-wrapper"
        :style="nodeStyle"
        :class="[classes, {'node-wrapper--execution': isExecution, 'node-wrapper--focused': focused, 'node-wrapper--dragging': dragging}]"
        :draggable="movable"
        @mouseover="mouseover"
        @mouseleave="mouseleave"
        @click="onCardClick"
        @dragstart="onDragStart"
        @dragend="emit(EVENTS.TASK_DRAG_END)"
    >
        <div class="main-content">
            <DragVertical v-if="movable" class="node-grip" aria-hidden="true" />
            <div class="icon" :class="{'icon--dimmed': statusStyle?.dimIcon}">
                <KsTooltip v-if="shortType" :content="shortType" placement="bottom" :showAfter="600">
                    <component :is="taskIconComponent" :cls="cls" :class="taskIconBg" variable="--ks-topology-icon-color" :icons="icons" :loadIcon="loadIcon" onlyIcon />
                </KsTooltip>
                <component v-else :is="taskIconComponent" :cls="cls" :class="taskIconBg" variable="--ks-topology-icon-color" :icons="icons" :loadIcon="loadIcon" onlyIcon />
            </div>
            <div class="node-content">
                <slot name="badge" />
                <div class="node-title">
                    <div class="task-title">
                        <KsTooltip v-if="extraTooltip" :content="extraTooltip">
                            {{ displayTitle }}
                        </KsTooltip>
                        <template v-else>{{ displayTitle }}</template>
                    </div>
                </div>
                <slot name="content" />
            </div>
            <slot name="title-status" />
            <slot name="title-actions" />
        </div>
        <slot name="details" />
    </div>
</template>

<script lang="ts" setup>
    import {computed, inject} from "vue"
    import {KsTooltip, useTaskIcon} from "@kestra-io/design-system"
    import DragVertical from "vue-material-design-icons/DragVertical.vue"
    import {EVENTS} from "../utils/constants"
    import {getStatusStyle} from "../utils/status"
    import {EXECUTION_INJECTION_KEY} from "../injectionKeys"
    import * as Utils from "../utils/utils"


    const emit = defineEmits([
        EVENTS.EXPAND,
        EVENTS.OPEN_LINK,
        EVENTS.SHOW_LOGS,
        EVENTS.MOUSE_OVER,
        EVENTS.MOUSE_LEAVE,
        EVENTS.ADD_ERROR,
        EVENTS.EDIT,
        EVENTS.DELETE,
        EVENTS.ADD_TASK,
        EVENTS.SHOW_DESCRIPTION,
        EVENTS.CARD_CLICK,
        EVENTS.TASK_DRAG_START,
        EVENTS.TASK_DRAG_END,
    ])

    const movable = computed(() => Boolean(props.data?.isMovable))

    // A 1x1 transparent gif replaces the browser's own drag image, which is an OS-level layer we
    // can neither style nor keep consistent across platforms; the graph draws its own instead.
    const TRANSPARENT_PIXEL =
        "data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7"

    function onDragStart(event: DragEvent) {
        if (!movable.value || !props.id) return
        if (event.dataTransfer) {
            event.dataTransfer.setData("text/plain", props.id)
            event.dataTransfer.effectAllowed = "move"
            const blank = new Image()
            blank.src = TRANSPARENT_PIXEL
            event.dataTransfer.setDragImage(blank, 0, 0)
        }
        emit(EVENTS.TASK_DRAG_START, {nodeId: props.id, label: displayTitle.value, cls: cls.value})
    }

    function onCardClick(event: MouseEvent) {
        const target = event.target as HTMLElement | null
        // The card is one big target, so anything that already has its own action keeps it.
        if (target?.closest("button, a, input, [role='button'], .vue-flow__handle")) return
        emit(EVENTS.CARD_CLICK, event)
    }

    defineOptions({
        name: "BasicNode",
        inheritAttrs: false,
    })

    const props = defineProps<{
        id?: string;
        title?: string;
        type?: string;
        disabled?: boolean;
        state?: string;
        data: any;
        icons: any;
        // Resolves an icon the `icons` index doesn't carry; without it a node whose plugin isn't
        // in the index has no way to ever get an icon (kestra-io/kestra#18129).
        loadIcon?: (cls: string) => Promise<any>;
        class?: string | string[] | Record<string, boolean>;
        focused?: boolean;
        dragging?: boolean;
    }>()

    const taskIconComponent = useTaskIcon()

    function mouseover() {
        emit(EVENTS.MOUSE_OVER, props.data.node)
    }

    function mouseleave() {
        emit(EVENTS.MOUSE_LEAVE)
    }

    const execution = inject(EXECUTION_INJECTION_KEY, undefined)
    const isExecution = computed(() => Boolean(execution?.value))

    const statusStyle = computed(() => getStatusStyle(props.state))

    const nodeStyle = computed(() => {
        const style = statusStyle.value
        if (!style) return undefined
        return {
            backgroundColor: style.bg,
            borderColor: style.border,
        }
    })

    const node = computed(() => {
        return props.data.node?.plugin ?? props.data.node?.task ?? props.data.node?.trigger ?? null
    })

    const trimmedId = computed(() => Utils.afterLastDot(props.id ?? ""))

    const taskIconBg = computed(() => {
        return !["default", "danger"].includes(props.data.color) ? props.data.color : ""
    })

    const classes = computed(() => {
        return [
            {
                "unused-path": props.data.unused,
                disabled: node.value?.disabled
                    || props.data.node?.disabled
                    || props.data.parent?.taskNode?.task?.disabled
                    || props.data.parent?.taskNode?.disabled,
            },
            props.class,
        ]
    })

    const cls = computed(() => {
        if (props.data.node.triggerDeclaration) {
            return props.data.node.triggerDeclaration.type
        }
        if (!node.value) return undefined
        return node.value?.type
    })

    const hoverTooltip = computed(() => {
        if (node.value?.type?.endsWith("SubflowGraphTask")) {
            const subflowIdContainer = node.value.subflowId ?? node.value
            return subflowIdContainer.namespace + " " + subflowIdContainer.flowId
        }
        return trimmedId.value
    })

    const displayTitle = computed(() => props.title ?? trimmedId.value)

    // The full class is what made the old hover box wide; every core and plugin task shares the
    // same `io.kestra.plugin.` prefix, so dropping it leaves the part that identifies the task.
    const shortType = computed(() => cls.value?.replace(/^io\.kestra\.plugin\./, ""))

    // On a plain task the tooltip only repeated the label already on the card, in a second box on
    // top of the native one; a subflow is the only node whose tooltip says something else.
    const extraTooltip = computed(() =>
        hoverTooltip.value === displayTitle.value ? undefined : hoverTooltip.value,
    )
</script>

<style lang="scss" scoped>
    .node-grip {
        display: flex;
        align-items: center;
        flex-shrink: 0;
        margin-left: calc(var(--ks-spacing-1) * -1);
        color: var(--ks-icon-inactive);
        cursor: grab;
        opacity: 0;
        transition: opacity 0.15s;

        .node-wrapper:hover & {
            opacity: 1;
        }

        &:active {
            cursor: grabbing;
        }
    }

    .node-wrapper--focused {
        outline: 2px solid var(--ks-border-focus);
        outline-offset: 2px;
    }

    /* The card the user picked up: it stays in the layout as the hole the task came out of. */
    .node-wrapper--dragging {
        opacity: 0.35;
    }

    .node-wrapper {
        background-color: var(--ks-bg-surface);
        border-radius: var(--ks-radius-base);
        overflow: hidden;
        margin: 0;
        z-index: 150000;
        box-shadow: 0 2px 4px var(--ks-shadow-surface);
        border: 1px solid var(--ks-border-strong);

        .main-content {
            display: flex;
            padding: var(--ks-spacing-2);
            padding-right: var(--ks-spacing-4);
            align-items: center;
            gap: var(--ks-spacing-1);
            width: 218px;
            height: 56px;
        }

        &--execution .main-content {
            width: 273px;
        }

        &.execution-no-taskrun, &.disabled {
            background-color: var(--ks-bg-surface);
        }

        &.disabled {
            .task-title {
                color: var(--ks-text-secondary);
                text-decoration: line-through;
            }
        }

        .icon {
            border-radius: var(--ks-radius-lg);
            width: 40px;
            height: 40px;
            min-width: 40px;
            min-height: 40px;
            padding: 3px;
            box-sizing: border-box;
            border: 1px solid var(--ks-border-default);
            background-color: var(--ks-topology-icon-bg);

            &--dimmed {
                opacity: 0.2;
            }
        }
    }

    .node-content {
        display: flex;
        flex-direction: column;
        justify-content: center;
        margin-left: var(--ks-spacing-2);
        flex: 1;
        min-width: 0;

        > .node-title {
            display: flex;
            align-items: center;
            min-width: 0;
            gap: var(--ks-spacing-1);
        }
    }

    .material-design-icon.icon-rounded {
        border-radius: 1rem;
        padding: 1px;
    }

    .button-icon {
        font-size: var(--ks-font-size-sm);
        transform: rotate(45deg);
    }

    .task-title {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        font-size: var(--ks-font-size-sm);
        font-weight: 500;
        color: var(--ks-text-primary);
        flex-grow: 1;
    }

</style>
