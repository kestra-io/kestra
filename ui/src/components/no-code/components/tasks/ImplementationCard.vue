<template>
    <div class="implementation-card" :class="{'implementation-card--error': unresolved}" data-test="plugin-implementation-card">
        <div class="implementation-card-head">
            <button
                type="button"
                class="implementation-card-toggle"
                :aria-expanded="expanded"
                :aria-controls="bodyId"
                data-test="plugin-implementation-toggle"
                @click="expanded = !expanded"
            >
                <ChevronRight class="implementation-card-chevron" :class="{'implementation-card-chevron--open': expanded}" />
                <span class="implementation-card-glyph">
                    <AlertCircleOutline v-if="unresolved" />
                    <TaskIcon v-else :cls="branch?.ref ?? ''" :icons="pluginsStore.icons" :loadIcon="pluginsStore.loadIcon" :onlyIcon="true" />
                </span>
                <span class="implementation-card-main">
                    <span class="implementation-card-name" data-test="plugin-implementation-item-name">{{ name }}</span>
                    <span v-if="summaryText" class="implementation-card-summary" data-test="plugin-implementation-item-summary">{{ summaryText }}</span>
                </span>
            </button>
            <div class="implementation-card-actions">
                <KsButton
                    v-if="canChange || unresolved"
                    size="small"
                    data-test="plugin-implementation-change"
                    @click.stop="emit('change')"
                >
                    {{ unresolved ? $t("block_editor.plugin_implementation.replace") : $t("block_editor.plugin_implementation.change") }}
                </KsButton>
                <KsIconButton
                    v-if="!unresolved"
                    :tooltip="removeLabel"
                    data-test="plugin-implementation-remove"
                    @click.stop="emit('remove')"
                >
                    <DeleteOutline />
                </KsIconButton>
            </div>
        </div>
        <div v-show="expanded" :id="bodyId" class="implementation-card-body">
            <template v-if="unresolved">
                <KsAlert
                    type="error"
                    :closable="false"
                    :title="$t('block_editor.plugin_implementation.unresolved_message', {type: storedType, count: branches.length, label: props.label})"
                    data-test="plugin-implementation-unresolved-error"
                />
                <KsAlert type="info" :closable="false" :title="$t('block_editor.plugin_implementation.unresolved_untouched')" />
                <p class="implementation-card-raw-label">{{ $t("block_editor.plugin_implementation.stored_value") }}</p>
                <pre class="implementation-card-raw" data-test="plugin-implementation-raw">{{ rawValue }}</pre>
            </template>
            <template v-else-if="branch">
                <span class="implementation-card-type" data-test="plugin-implementation-type">{{ branch.ref }}</span>
                <TaskObject
                    :schema="branch.definition"
                    :modelValue="modelValue"
                    :root="root"
                    filterType
                    @update:model-value="(value) => emit('update:modelValue', value)"
                />
            </template>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed, ref, useId} from "vue"
    import {useI18n} from "vue-i18n"
    import * as YAML_UTILS from "@kestra-io/topology/flow-yaml-utils"
    import {KsAlert, KsButton, KsIconButton} from "@kestra-io/design-system"
    import ChevronRight from "vue-material-design-icons/ChevronRight.vue"
    import AlertCircleOutline from "vue-material-design-icons/AlertCircleOutline.vue"
    import DeleteOutline from "vue-material-design-icons/DeleteOutline.vue"
    import TaskIcon from "../../../plugins/TaskIcon.vue"
    import TaskObject from "./TaskObject.vue"
    import {usePluginsStore} from "../../../../stores/plugins"
    import {
        findBranchByType,
        humanizeClassName,
        humanizePropertyKey,
        simpleClassName,
        summarizeImplementationValue,
        type ImplementationBranch,
    } from "./discriminatedUnion"

    const props = defineProps<{
        modelValue?: Record<string, unknown>
        branches: ImplementationBranch[]
        canChange: boolean
        label: string
        removeLabel: string
        root?: string
    }>()

    const emit = defineEmits<{
        "update:modelValue": [value: Record<string, unknown> | undefined]
        remove: []
        change: []
    }>()

    const {t} = useI18n()

    const pluginsStore = usePluginsStore()

    const bodyId = useId()

    const storedType = computed(() => {
        const type = props.modelValue?.type
        return typeof type === "string" ? type : undefined
    })

    const branch = computed(() => storedType.value ? findBranchByType(props.branches, storedType.value) : undefined)

    const unresolved = computed(() => storedType.value !== undefined && branch.value === undefined)

    const expanded = ref(unresolved.value)

    const name = computed(() => branch.value
        ? humanizeClassName(simpleClassName(branch.value.ref))
        : t("block_editor.plugin_implementation.unresolved_name"))

    const summaryText = computed(() => {
        if (!branch.value) return storedType.value ?? ""

        const summary = summarizeImplementationValue(props.modelValue, branch.value.definition)
        if (summary.kind === "empty") {
            return branch.value.definition.description ?? branch.value.definition.markdownDescription ?? ""
        }

        const parts = summary.parts.map((part) =>
            part.secret ? t("block_editor.plugin_implementation.secret_summary", {label: humanizePropertyKey(part.key)}) : part.text,
        )
        if (summary.overflow > 0) {
            parts.push(t("block_editor.plugin_implementation.summary_overflow", {count: summary.overflow}))
        }
        return parts.join(" · ")
    })

    const rawValue = computed(() => YAML_UTILS.stringify(props.modelValue))
</script>

<style scoped lang="scss">
.implementation-card {
    border: 1px solid var(--ks-border-default);
    border-radius: var(--ks-radius-base);
    background: var(--ks-bg-surface);
}

.implementation-card--error {
    border-color: var(--ks-border-error);
    background: var(--ks-bg-error);
}

.implementation-card-head {
    display: flex;
    align-items: stretch;
    gap: var(--ks-spacing-1);
    padding-right: var(--ks-spacing-2);
}

.implementation-card-toggle {
    flex: 1 1 auto;
    min-width: 0;
    display: flex;
    align-items: center;
    gap: var(--ks-spacing-3);
    padding: var(--ks-spacing-3);
    background: none;
    border: none;
    cursor: pointer;
    text-align: left;
    border-radius: var(--ks-radius-base);
}

.implementation-card-toggle:hover {
    background: var(--ks-bg-hover);
}

.implementation-card-toggle:focus-visible {
    outline: 2px solid var(--ks-border-focus);
    outline-offset: -2px;
}

.implementation-card-chevron {
    flex-shrink: 0;
    color: var(--ks-icon-default);
    transition: transform 0.12s ease;
}

.implementation-card-chevron--open {
    transform: rotate(90deg);
}

.implementation-card-glyph {
    display: flex;
    align-items: center;
    justify-content: center;
    width: var(--ks-icon-size-lg);
    height: var(--ks-icon-size-lg);
    flex-shrink: 0;
    border: 1px solid var(--ks-border-default);
    border-radius: var(--ks-radius-xs);
    background: var(--ks-bg-elevated);
    color: var(--ks-icon-default);
}

.implementation-card-main {
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 2px;
}

.implementation-card-name {
    font-size: var(--ks-font-size-sm);
    font-weight: 600;
    color: var(--ks-text-primary);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.implementation-card--error .implementation-card-name {
    color: var(--ks-text-error);
}

.implementation-card-summary {
    font-size: var(--ks-font-size-xs);
    color: var(--ks-text-secondary);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.implementation-card--error .implementation-card-summary {
    font-family: var(--ks-font-family-mono);
}

.implementation-card-actions {
    display: flex;
    align-items: center;
    gap: var(--ks-spacing-1);
    flex: none;
    align-self: center;
}

.implementation-card-body {
    padding: var(--ks-spacing-4);
    border-top: 1px solid var(--ks-border-default);
    background: var(--ks-bg-elevated);
    border-radius: 0 0 var(--ks-radius-base) var(--ks-radius-base);
    display: flex;
    flex-direction: column;
    gap: var(--ks-spacing-2);
}

.implementation-card--error .implementation-card-body {
    background: var(--ks-bg-surface);
}

.implementation-card-type {
    display: inline-flex;
    align-items: center;
    gap: var(--ks-spacing-1);
    font-family: var(--ks-font-family-mono);
    font-size: var(--ks-font-size-xs);
    color: var(--ks-text-dim);
    margin-bottom: var(--ks-spacing-2);
    overflow-wrap: anywhere;
}

.implementation-card-raw-label {
    margin: 0;
    font-size: var(--ks-font-size-xs);
    font-weight: 600;
    color: var(--ks-text-muted);
}

.implementation-card-raw {
    margin: 0;
    padding: var(--ks-spacing-3);
    background: var(--ks-bg-input);
    border: 1px solid var(--ks-border-default);
    border-radius: var(--ks-radius-sm);
    font-family: var(--ks-font-family-mono);
    font-size: var(--ks-font-size-xs);
    color: var(--ks-text-primary);
    white-space: pre-wrap;
    overflow-wrap: anywhere;
}
</style>
