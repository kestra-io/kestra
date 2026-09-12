<template>
    <div class="flow-chip">
        <div class="flow-chip-head">
            <button
                type="button"
                class="flow-chip-identity"
                data-test="topology-flow-chip"
                @click="emit(EVENTS.EDIT_FLOW)"
            >
                <FileDocumentOutline class="flow-chip-icon" />
                <KsTooltip :content="fullPath" placement="bottom">
                    <span class="flow-chip-path">
                        <span class="flow-chip-namespace">{{ namespace }}</span>
                        <span class="flow-chip-separator">/</span>
                        <span class="flow-chip-id">{{ flowId }}</span>
                    </span>
                </KsTooltip>
            </button>

            <KsTooltip :content="$t('block_editor.configure')" placement="bottom">
                <button
                    type="button"
                    class="flow-chip-action"
                    :aria-label="$t('block_editor.configure')"
                    data-test="topology-flow-configure"
                    @click="emit(EVENTS.EDIT_FLOW)"
                >
                    <Cog :size="16" />
                </button>
            </KsTooltip>

            <button
                v-if="hasDetails"
                type="button"
                class="flow-chip-action"
                :aria-label="$t(expanded ? 'collapse' : 'expand')"
                :aria-expanded="expanded"
                data-test="topology-flow-expand"
                @click="expanded = !expanded"
            >
                <ChevronDown class="flow-chip-chevron" :class="{'flow-chip-chevron--open': expanded}" :size="16" />
            </button>
        </div>

        <div v-if="hasDetails && expanded" class="flow-chip-details">
            <span v-if="description" class="flow-chip-description">{{ description }}</span>
            <span v-if="labels.length" class="flow-chip-labels">
                <KsTag v-for="[key, value] in labels" :key="key">{{ key }}: {{ value }}</KsTag>
            </span>
        </div>
    </div>
</template>

<script lang="ts" setup>
    import {computed, ref} from "vue"
    import {KsTag, KsTooltip} from "@kestra-io/design-system"
    import FileDocumentOutline from "vue-material-design-icons/FileDocumentOutline.vue"
    import Cog from "vue-material-design-icons/Cog.vue"
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue"
    import {EVENTS} from "../utils/constants"

    defineOptions({name: "FlowSummaryChip"})

    const props = withDefaults(
        defineProps<{
            flowId?: string;
            namespace?: string;
            description?: string;
            labels?: [string, string][];
        }>(),
        {flowId: undefined, namespace: undefined, description: undefined, labels: () => []},
    )

    const emit = defineEmits([EVENTS.EDIT_FLOW])

    const expanded = ref(false)
    const hasDetails = computed(() => Boolean(props.description) || props.labels.length > 0)
    const fullPath = computed(() => [props.namespace, props.flowId].filter(Boolean).join(" / "))
</script>

<style lang="scss" scoped>
    .flow-chip {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-2);
        max-width: 100%;
        padding: var(--ks-spacing-1) var(--ks-spacing-2);
        /* The canvas is --ks-bg-base, where --ks-border-subtle resolves to the same value and would
           be invisible; the dot grid also reads through a transparent fill. */
        background: var(--ks-bg-surface);
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-base);
        box-shadow: 0 1px 2px var(--ks-shadow-surface);
    }

    .flow-chip-head {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-1);
        min-width: 0;
    }

    .flow-chip-identity {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        min-width: 0;
        padding: var(--ks-spacing-1);
        background: none;
        border: none;
        border-radius: var(--ks-radius-sm);
        cursor: pointer;

        &:hover {
            background: var(--ks-bg-hover);
        }

        &:focus-visible {
            outline: 2px solid var(--ks-border-focus);
            outline-offset: 1px;
        }
    }

    .flow-chip-icon {
        display: flex;
        flex-shrink: 0;
        color: var(--ks-icon-default);
    }

    .flow-chip-path {
        display: flex;
        align-items: baseline;
        gap: var(--ks-spacing-1);
        min-width: 0;
        font-size: var(--ks-font-size-sm);
    }

    /* The namespace gives way first: losing the flow id would cost the identity, while losing the
       namespace only costs context that the tooltip still carries. */
    .flow-chip-namespace {
        flex: 0 1 auto;
        min-width: 0;
        overflow: hidden;
        white-space: nowrap;
        text-overflow: ellipsis;
        color: var(--ks-text-secondary);
    }

    .flow-chip-separator {
        flex-shrink: 0;
        color: var(--ks-text-muted);
    }

    .flow-chip-id {
        flex: 0 1 auto;
        min-width: 0;
        overflow: hidden;
        white-space: nowrap;
        text-overflow: ellipsis;
        font-weight: 600;
        color: var(--ks-text-primary);
    }

    .flow-chip-action {
        display: flex;
        flex-shrink: 0;
        padding: var(--ks-spacing-1);
        background: none;
        border: none;
        border-radius: var(--ks-radius-sm);
        color: var(--ks-icon-default);
        cursor: pointer;

        &:hover {
            background: var(--ks-bg-hover);
            color: var(--ks-icon-hover);
        }

        &:focus-visible {
            outline: 2px solid var(--ks-border-focus);
            outline-offset: 1px;
        }
    }

    .flow-chip-chevron {
        transition: transform 0.15s ease;
    }

    .flow-chip-chevron--open {
        transform: rotate(180deg);
    }

    .flow-chip-details {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-2);
        padding: 0 var(--ks-spacing-1) var(--ks-spacing-1);
    }

    .flow-chip-description {
        font-size: var(--ks-font-size-xs);
        color: var(--ks-text-secondary);
        overflow: hidden;
        text-overflow: ellipsis;
        display: -webkit-box;
        -webkit-line-clamp: 2;
        -webkit-box-orient: vertical;
    }

    .flow-chip-labels {
        display: flex;
        flex-wrap: wrap;
        gap: var(--ks-spacing-1);
    }

    @media (prefers-reduced-motion: reduce) {
        .flow-chip-chevron {
            transition: none;
        }
    }
</style>
