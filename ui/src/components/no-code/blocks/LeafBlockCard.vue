<template>
    <div
        class="leaf-block-card"
        :class="{'leaf-block-card--selected': selected, 'leaf-block-card--drag-over': dragOver, 'block-kbd-focused': focused, 'leaf-block-card--error': issues.length > 0}"
        role="button"
        :tabindex="focused ? 0 : -1"
        :aria-pressed="selected"
        :aria-selected="focused"
        :aria-label="cardAriaLabel"
        :draggable="draggable"
        data-test="block-card"
        @click="emit('select')"
        @dragstart="emit('drag-start', $event)"
        @dragover.prevent="emit('drag-over', $event)"
        @drop.prevent="emit('drop', $event)"
        @dragend="emit('drag-end')"
    >
        <DragVertical
            v-if="draggable"
            class="leaf-block-card-grip"
            :aria-label="t('block_editor.drag_reorder')"
            @mousedown.stop
        />

        <TaskIcon
            class="leaf-block-card-ico"
            :cls="String(displayBlock.type ?? '')"
            :icons="icons"
            :loadIcon="pluginsStore.loadIcon"
            :onlyIcon="true"
        />

        <div class="leaf-block-card-main">
            <div class="leaf-block-card-idrow">
                <span class="leaf-block-card-id" data-test="block-card-id">{{ displayLabel }}</span>
                <BlockErrorBadge :issues="issues" />
            </div>
            <span v-if="showType" class="leaf-block-card-type" data-test="block-card-type">{{ shortType }}</span>
        </div>

        <div class="leaf-block-card-actions">
            <KsIconButton
                v-if="runnable"
                class="leaf-block-card-action leaf-block-card-action--run"
                :aria-label="t('playground.run_task')"
                :tooltip="t('playground.run_task')"
                data-test="block-card-run"
                tabindex="-1"
                @click.stop="emit('run')"
            >
                <Play />
            </KsIconButton>

            <KsIconButton
                v-if="showOpenSplit && !panelMaximized"
                class="leaf-block-card-action"
                :aria-label="t('block_editor.open_in_split')"
                :tooltip="t('block_editor.open_in_split')"
                data-test="block-card-open-split"
                tabindex="-1"
                @click.stop="emit('open-split')"
            >
                <ViewSplitVertical />
            </KsIconButton>

            <KsIconButton
                v-if="showDuplicate"
                class="leaf-block-card-action"
                :aria-label="t('block_editor.duplicate')"
                :tooltip="`${t('block_editor.duplicate')} (d)`"
                data-test="block-card-duplicate"
                tabindex="-1"
                @click.stop="emit('duplicate')"
            >
                <ContentCopy />
            </KsIconButton>

            <KsIconButton
                class="leaf-block-card-action leaf-block-card-action--danger"
                :aria-label="t('block_editor.delete')"
                :tooltip="`${t('block_editor.delete')} (⌫)`"
                data-test="block-card-delete"
                tabindex="-1"
                @click.stop="emit('delete')"
            >
                <DeleteOutline />
            </KsIconButton>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed, inject} from "vue"
    import {useI18n} from "vue-i18n"
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue"
    import DeleteOutline from "vue-material-design-icons/DeleteOutline.vue"
    import DragVertical from "vue-material-design-icons/DragVertical.vue"
    import Play from "vue-material-design-icons/Play.vue"
    import ViewSplitVertical from "vue-material-design-icons/ViewSplitVertical.vue"

    import {KsIconButton} from "@kestra-io/design-system"
    import TaskIcon from "../../plugins/TaskIcon.vue"
    import BlockErrorBadge from "./BlockErrorBadge.vue"

    import {usePluginsStore, type PluginIconData} from "../../../stores/plugins"
    import {displayTaskOf} from "../../../utils/flowableBlockOps"
    import {BLOCK_VALIDATION_ISSUES_INJECTION_KEY, PANEL_MAXIMIZED_INJECTION_KEY} from "../injectionKeys"

    const {t} = useI18n()

    const pluginsStore = usePluginsStore()

    const props = withDefaults(defineProps<{
        block: Record<string, unknown>
        path: string
        label?: string
        selected?: boolean
        focused?: boolean
        draggable?: boolean
        dragOver?: boolean
        runnable?: boolean
        showOpenSplit?: boolean
        showDuplicate?: boolean
        icons?: Record<string, PluginIconData>
    }>(), {
        showOpenSplit: true,
        showDuplicate: true,
    })

    const emit = defineEmits<{
        (e: "select"): void
        (e: "open-split"): void
        (e: "delete"): void
        (e: "duplicate"): void
        (e: "run"): void
        (e: "drag-start", event: DragEvent): void
        (e: "drag-over", event: DragEvent): void
        (e: "drop", event: DragEvent): void
        (e: "drag-end"): void
    }>()

    const displayBlock = computed(() => displayTaskOf(props.block))

    const validationIssues = inject(BLOCK_VALIDATION_ISSUES_INJECTION_KEY, undefined)
    const panelMaximized = inject(PANEL_MAXIMIZED_INJECTION_KEY, undefined)
    const issues = computed<string[]>(() =>
        validationIssues?.value?.get(String(displayBlock.value.id ?? "")) ?? [],
    )

    const shortType = computed(() => {
        const type = String(displayBlock.value.type ?? "")
        const parts = type.split(".")
        return parts[parts.length - 1] ?? type
    })

    const displayLabel = computed(() => props.label ?? String(displayBlock.value.id ?? ""))

    const showType = computed(() => !!shortType.value && shortType.value !== displayLabel.value)

    const cardAriaLabel = computed(() =>
        t("block_editor.card_aria_label", {id: displayLabel.value, type: shortType.value}),
    )
</script>

<style scoped lang="scss">
    .leaf-block-card {
        position: relative;
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-3);
        padding: var(--ks-spacing-2) var(--ks-spacing-3);
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-base);
        background: var(--ks-btn-secondary-bg-default);
        cursor: pointer;
        transition: border-color 0.15s, background-color 0.15s, box-shadow 0.15s;
        outline: none;

        &:hover {
            background: var(--ks-bg-surface);
            border-color: var(--ks-border-strong);
            box-shadow: var(--ks-shadow-sm);
        }

        &:focus-visible {
            border-color: var(--ks-border-focus);
            box-shadow: 0 0 0 2px var(--ks-border-focus);
        }

        &--selected {
            border-color: var(--ks-border-focus);
            background: var(--ks-bg-active);
        }

        &--drag-over {
            border-color: var(--ks-text-link);
            border-style: dashed;
        }

        &--error,
        &--error:hover {
            border-color: var(--ks-border-error);
            box-shadow: inset var(--ks-spacing-1) 0 0 var(--ks-border-error);
        }
    }

    .leaf-block-card-grip {
        flex-shrink: 0;
        color: var(--ks-icon-inactive);
        cursor: grab;
        display: flex;
        font-size: var(--ks-font-size-sm);
        opacity: 0;
        transition: opacity 0.15s;

        .leaf-block-card:hover & {
            opacity: 1;
        }

        &:active {
            cursor: grabbing;
        }
    }

    .leaf-block-card-ico {
        flex-shrink: 0;
        width: var(--ks-icon-size-lg);
        height: var(--ks-icon-size-lg);
    }

    .leaf-block-card-main {
        display: flex;
        flex-direction: column;
        flex: 1;
        min-width: 0;
        gap: 1px;
    }

    .leaf-block-card-idrow {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        min-width: 0;
    }

    .leaf-block-card-id {
        font-size: var(--ks-font-size-sm);
        font-weight: 600;
        font-family: var(--ks-font-family-mono);
        color: var(--ks-text-primary);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .leaf-block-card-type {
        font-size: var(--ks-font-size-xs);
        color: var(--ks-text-muted);
        font-family: var(--ks-font-family-mono);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .leaf-block-card-actions {
        position: absolute;
        right: var(--ks-spacing-2);
        top: 50%;
        transform: translateY(-50%);
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-1);
        padding-left: var(--ks-spacing-3);
        background: var(--ks-bg-hover);
        border-radius: var(--ks-radius-base);
        opacity: 0;
        pointer-events: none;
        transition: opacity 0.15s;

        .leaf-block-card:hover &,
        .leaf-block-card:focus-within & {
            opacity: 1;
            pointer-events: auto;
        }
    }

    .leaf-block-card-action {
        &--run:hover {
            color: var(--ks-text-success);
        }

        &--danger:hover {
            color: var(--ks-text-error);
        }
    }

    .block-kbd-focused {
        box-shadow: 0 0 0 2px var(--ks-border-focus);
    }
</style>
