<template>
    <div
        class="node-wrapper"
        :style="nodeStyle"
        :class="[classes, {'node-wrapper--execution': isExecution}]"
        @mouseover="mouseover"
        @mouseleave="mouseleave"
    >
        <div class="main-content">
            <div class="icon" :class="{'icon--dimmed': statusStyle?.dimIcon}">
                <component :is="iconComponent || TaskIcon" :cls="cls" :class="taskIconBg" theme="light" variable="--ks-topology-icon-color" :icons="icons" />
            </div>
            <div class="node-content">
                <slot name="badge" />
                <div class="node-title">
                    <div class="task-title" :title="hoverTooltip">
                        <KsTooltip :content="hoverTooltip">
                            {{ displayTitle }}
                        </KsTooltip>
                    </div>
                    <slot name="title-status" />
                    <slot name="title-actions" />
                </div>
                <slot name="content" />
            </div>
        </div>
        <slot name="details" />
    </div>
</template>

<script lang="ts" setup>
    import {computed, inject} from "vue"
    import TaskIcon from "../components/TaskIcon.vue"
    import {KsTooltip} from "@kestra-io/design-system"
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
    ])

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
        iconComponent: any;
        class?: string | string[] | Record<string, boolean>;
    }>()

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
                disabled: node.value?.disabled || props.data.parent?.taskNode?.task?.disabled,
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
</script>

<style lang="scss" scoped>
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
        margin-left: 0.7rem;
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
        font-size: 0.75rem;
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
