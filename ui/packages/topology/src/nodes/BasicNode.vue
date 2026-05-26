<template>
    <div
        class="node-wrapper"
        :style="nodeStyle"
        :class="{...classes, 'running-border-animation': state === 'RUNNING'}"
        @mouseover="mouseover"
        @mouseleave="mouseleave"
    >
        <!-- Runner name badge: floats above the top-left corner, orange pre-execution / state-colored post-execution -->
        <div v-if="hasTaskRunner" class="runner-badge" :style="runnerBadgeStyle">
            {{ runnerLabel }}
        </div>
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

    const hasTaskRunner = computed(() => Boolean(props.data.node?.task?.taskRunner))

    /**
     * Derive a short human-readable label from the taskRunner type.
     * e.g. "io.kestra.plugin.aws.runner.Batch" → "AWS BATCH"
     *      "io.kestra.plugin.gcp.runner.CloudRun" → "GCP CLOUD RUN"
     *      "io.kestra.plugin.kubernetes.runner.Kubernetes" → "KUBERNETES"
     */
    const runnerLabel = computed(() => {
        const type = props.data.node?.task?.taskRunner?.type as string | undefined
        if (!type) return ""
        const parts = type.split(".")
        const runnerIdx = parts.findIndex((p: string) => p === "runner")
        if (runnerIdx < 0) {
            // No 'runner' segment — just split camelCase on the last part
            return parts[parts.length - 1].replace(/([A-Z])/g, " $1").trim().toUpperCase()
        }
        const plugin = runnerIdx > 0 ? parts[runnerIdx - 1].toUpperCase() : ""
        const cls = runnerIdx < parts.length - 1
            ? parts[runnerIdx + 1].replace(/([A-Z])/g, " $1").trim().toUpperCase()
            : ""
        // Avoid duplication like "KUBERNETES KUBERNETES"
        if (cls.includes(plugin)) return cls.trim()
        return `${plugin} ${cls}`.trim()
    })

    /** Badge and border share the same color: orange pre-execution, state-colored post-execution. */
    const runnerAccentColor = computed(() =>
        props.state
            ? `var(--ks-border-${props.state.toLowerCase()})`
            : "var(--ks-topology-task-runner-border)",
    )

    const runnerBadgeStyle = computed(() => ({
        backgroundColor: runnerAccentColor.value,
    }))

    /**
     * Node border:
     * - task-runner, no state  → 2px dashed orange
     * - task-runner, with state → 2px solid state-color  (same inline override as before for non-runner nodes)
     * - no runner, with state  → border-color only (default 1px width stays from CSS)
     * - no runner, no state   → nothing (CSS default 1px solid ks-border-primary)
     */
    const nodeStyle = computed(() => {
        if (hasTaskRunner.value) {
            return {
                borderColor: runnerAccentColor.value,
                borderStyle: props.state ? "solid" : "dashed",
                borderWidth: "2px",
            }
        }
        return props.state
            ? {borderColor: `var(--ks-border-${props.state.toLowerCase()})`}
            : {}
    })

    const classes = computed(() => {
        return [
            {
                "unused-path": props.data.unused,
                disabled: node.value?.disabled || props.data.parent?.taskNode?.task?.disabled,
                "with-task-runner": hasTaskRunner.value,
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
        position: relative; // required for .runner-badge absolute positioning
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

        &.with-task-runner {
            // Border style (color, width, dashed/solid) is fully controlled by the
            // inline `nodeStyle` computed — nothing to override here.
            // The class is kept as a CSS hook for future runner-specific tweaks.
        }

        .runner-badge {
            position: absolute;
            // Bottom of the badge sits flush with the node's top border.
            top: 0;
            left: -1px; // align with the node's left border edge
            transform: translateY(-100%);
            padding: 2px 10px;
            border-radius: 999px;
            font-size: 0.6rem;
            font-weight: 700;
            color: white;
            letter-spacing: 0.06em;
            line-height: 1.6;
            white-space: nowrap;
            user-select: none;
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
