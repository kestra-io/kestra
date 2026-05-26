<template>
    <div
        class="node-wrapper"
        :style="{borderColor: state ? `var(--ks-border-${state.toLowerCase()})` : undefined}"
        :class="{...classes, 'running-border-animation': state === 'RUNNING'}"
        @mouseover="mouseover"
        @mouseleave="mouseleave"
    >
        <div class="main-content">
            <div class="icon">
                <component :is="iconComponent || TaskIcon" :cls="cls" :class="taskIconBg" class="bg-white" theme="light" :icons="icons" />
            </div>
            <div class="node-content">
                <div class="node-title">
                    <div class="task-title" :title="hoverTooltip">
                        <KsTooltip :content="hoverTooltip">
                            {{ displayTitle }}
                        </KsTooltip>
                    </div>
                    <span class="description-wrapper" v-if="description">
                        <KsTooltip :content="$t('show description')" class="description-tooltip">
                            <InformationOutline
                                @click="$emit(EVENTS.SHOW_DESCRIPTION, {id: trimmedId, description: description})"
                                class="description-button"
                            />
                        </KsTooltip>
                    </span>
                </div>
                <slot name="content" />
            </div>
            <div class="top-button-div">
                <slot name="badge-button-before" />
                <span
                    v-if="data.link"
                    class="circle-button"
                    :style="{backgroundColor: `var(--ks-node-${data.color})`}"
                    @click="$emit(EVENTS.OPEN_LINK, {link: data.link})"
                >
                    <KsTooltip :content="$t('open')">
                        <OpenInNew class="button-icon" alt="Open in new tab" />
                    </KsTooltip>
                </span>
                <span
                    v-if="expandable"
                    class="circle-button"
                    :style="{backgroundColor: `var(--ks-node-${data.color})`}"
                    @click="$emit(EVENTS.EXPAND)"
                >
                    <KsTooltip :content="$t('expand')">
                        <ArrowExpand class="button-icon" alt="Expand task" />
                    </KsTooltip>
                </span>
                <slot name="badge-button-after" />
            </div>
        </div>
        <slot name="details" />
    </div>
</template>

<script lang="ts" setup>
    import {computed} from "vue"
    import TaskIcon from "../components/TaskIcon.vue"
    import {KsTooltip} from "@kestra-io/design-system"
    import InformationOutline from "vue-material-design-icons/InformationOutline.vue"
    import {EVENTS} from "../utils/constants"
    import ArrowExpand from "vue-material-design-icons/ArrowExpand.vue"
    import OpenInNew from "vue-material-design-icons/OpenInNew.vue"
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

    const expandable = computed(() => props.data?.expandable || false)

    const node = computed(() => {
        return props.data.node?.plugin ?? props.data.node?.task ?? props.data.node?.trigger ?? null
    })

    const description = computed(() => node.value?.description ?? null)

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
        border-radius: var(--ks-border-radius-lg);
        margin: 0;
        z-index: 150000;
        box-shadow: 0 12px 12px 0 rgba(130, 103, 158, 0.10);
        border: 1px solid var(--ks-border-primary);

        .main-content {
            display: flex;
            padding: 8px;
            align-items: center;
            width: 184px;
            height: 44px;
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
            border-radius: var(--ks-border-radius);
            margin: 0.2rem;
            width: 25px;
            height: 25px;
            border: 0.4px solid var(--ks-border-primary);
            min-width: 25px;
            min-height: 25px;
        }
    }

    .node-content {
        display: flex;
        flex-direction: column;
        justify-content: center;
        margin-left: 0.7rem;

        > .node-title {
            display: flex;
            width: 125px;
        }
    }

    .description-wrapper {
        display: flex;
    }

    .description-tooltip {
        display: flex;
        align-items: center;
    }

    .description-button {
        margin-left: 0.5rem;
        color: var(--ks-text-secondary);
        cursor: pointer;
    }

    .material-design-icon.icon-rounded {
        border-radius: 1rem;
        padding: 1px;
    }

    .button-icon {
        font-size: 0.75rem;
    }

    .task-title {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        font-size: 0.75rem;
        font-weight: 700;
        color: var(--ks-text-primary);
        flex-grow: 1;
    }

    .status-div {
        width: 8px;
        height: 100%;
        position: absolute;
        left: -0.04438rem;
        border-radius: 0.5rem 0 0 0.5rem;
    }

    .running-border-animation {
        border: none !important;
        &:before {
            position: absolute;
            content: '';
            z-index: -1;
            top: -1px;
            left: -1px;
            right: -1px;
            bottom: -1px;
            border-radius: .55rem;
            background: conic-gradient(from calc(var(--border-angle-running)) at 50% 50%,
                var(--ks-status-border-running) 0%,
                var(--ks-status-border-running) 10%,
                var(--ks-border-primary) 40%,
                var(--ks-border-primary) 60%,
                var(--ks-status-border-running) 90%,
                var(--ks-status-border-running) 100%);
            animation: running-border 3s linear infinite;
        }
    }

    @keyframes running-border {
        to { --border-angle-running: 1turn; }
    }

    @property --border-angle-running {
        syntax: "<angle>";
        inherits: true;
        initial-value: 0turn;
    }
</style>
