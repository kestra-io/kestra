<template>
    <div
        class="flowable-cluster"
        :class="{'flowable-cluster--expanded': expanded, 'flowable-cluster--error': issues.length > 0}"
        :data-test="`flowable-cluster-${String(displayBlock.id ?? '')}`"
    >
        <div
            class="flowable-cluster-header"
            :class="{'block-kbd-focused': focused}"
            role="button"
            :tabindex="focused ? 0 : -1"
            :aria-expanded="expanded"
            :aria-selected="focused"
            :aria-label="headerAriaLabel"
            data-test="flowable-cluster-header"
            @click="toggle"
        >
            <component
                :is="expanded ? ChevronDown : ChevronRight"
                class="flowable-cluster-chevron"
                aria-hidden="true"
            />

            <TaskIcon
                class="flowable-cluster-icon"
                :cls="String(displayBlock.type ?? '')"
                :icons="icons"
                :loadIcon="pluginsStore.loadIcon"
                :onlyIcon="true"
            />

            <span class="flowable-cluster-id" data-test="block-card-id">{{ displayBlock.id }}</span>

            <BlockErrorBadge :issues="issues" />

            <KsTag size="small" class="flowable-cluster-kind-tag" data-test="block-card-type">
                {{ shortType }}
            </KsTag>

            <span v-if="!expanded" class="flowable-cluster-summary" aria-hidden="true">
                {{ t("block_editor.collapsed_summary", {count: totalNestedCount}) }}
            </span>

            <div class="flowable-cluster-actions">
                <KsIconButton
                    :aria-label="t('block_editor.configure')"
                    :tooltip="t('block_editor.configure')"
                    data-test="flowable-cluster-configure"
                    tabindex="-1"
                    @click.stop="emit('select', path)"
                >
                    <Cog />
                </KsIconButton>

                <KsIconButton
                    :aria-label="t('block_editor.duplicate')"
                    :tooltip="`${t('block_editor.duplicate')} (d)`"
                    data-test="block-card-duplicate"
                    tabindex="-1"
                    @click.stop="emit('duplicate', path)"
                >
                    <ContentCopy />
                </KsIconButton>

                <KsIconButton
                    class="flowable-cluster-action--danger"
                    :aria-label="t('block_editor.delete')"
                    :tooltip="`${t('block_editor.delete')} (⌫)`"
                    data-test="block-card-delete"
                    tabindex="-1"
                    @click.stop="emit('delete', path)"
                >
                    <DeleteOutline />
                </KsIconButton>
            </div>
        </div>

        <div v-if="expanded" class="flowable-cluster-body">
            <BranchLane
                v-for="lane in lanes"
                :key="lane.name"
                :laneName="lane.name"
                :tasks="lane.tasks"
                :parentPath="laneParentPath(lane.name)"
                :icons="icons"
                :selectedId="selectedId"
                :focusedId="focusedId"
                :depth="depth"
                :playgroundEnabled="playgroundEnabled"
                @select="(p) => emit('select', p)"
                @open-split="(p) => emit('open-split', p)"
                @delete="(p) => emit('delete', p)"
                @duplicate="(p) => emit('duplicate', p)"
                @run="(id) => emit('run', id)"
                @add-at-path="(p, afterIdx, evt) => emit('add-at-path', p, afterIdx, evt)"
                @update-depends-on="(p, dependsOn) => emit('update-depends-on', p, dependsOn)"
                @reorder="(p, from, to) => emit('reorder', p, from, to)"
            />

            <div v-if="isSwitchTask" class="flowable-cluster-add-case">
                <KsInput
                    v-model="newCaseKey"
                    :placeholder="t('block_editor.switch_case_key_placeholder')"
                    :aria-label="t('block_editor.switch_case_key_placeholder')"
                    size="small"
                    class="flowable-cluster-case-input"
                    data-test="flowable-add-case-input"
                    @keydown.enter.prevent="addCase"
                />
                <button
                    class="flowable-cluster-add-case-btn"
                    type="button"
                    :disabled="!newCaseKey.trim()"
                    data-test="flowable-add-case-btn"
                    @click="addCase"
                >
                    <PlusCircleOutline class="flowable-cluster-add-icon" />
                    {{ t("block_editor.add_switch_case") }}
                </button>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed, defineAsyncComponent, inject, ref} from "vue"
    import {useI18n} from "vue-i18n"
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue"
    import ChevronRight from "vue-material-design-icons/ChevronRight.vue"
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue"
    import Cog from "vue-material-design-icons/CogOutline.vue"
    import DeleteOutline from "vue-material-design-icons/DeleteOutline.vue"
    import PlusCircleOutline from "vue-material-design-icons/PlusCircleOutline.vue"

    import {KsTag, KsIconButton, KsInput} from "@kestra-io/design-system"
    import TaskIcon from "../../plugins/TaskIcon.vue"
    import BlockErrorBadge from "./BlockErrorBadge.vue"

    import {usePluginsStore, type PluginIconData} from "../../../stores/plugins"
    import {displayTaskOf, taskEditPathFor} from "../../../utils/flowableBlockOps"
    import {flowYamlUtils} from "@kestra-io/topology"
    import {BLOCK_VALIDATION_ISSUES_INJECTION_KEY} from "../injectionKeys"

    const BranchLane = defineAsyncComponent(() => import("./BranchLane.vue"))

    const {t} = useI18n()

    const pluginsStore = usePluginsStore()

    const FLOWABLE_SUFFIX_MAP: Record<string, string[]> = {
        "If": ["then", "else", "errors", "finally"],
        "Switch": ["cases", "defaults", "errors", "finally"],
        "Parallel": ["tasks", "errors", "finally"],
        "Sequential": ["tasks", "errors", "finally"],
        "ForEach": ["tasks", "errors", "finally"],
        "EachSequential": ["tasks", "errors", "finally"],
        "Dag": ["tasks", "errors", "finally"],
        "WaitFor": ["tasks", "errors", "finally"],
        "ForEachItem": ["tasks", "errors", "finally"],
    }

    const props = defineProps<{
        block: Record<string, unknown>
        path: string
        domId?: string
        icons?: Record<string, PluginIconData>
        selectedId?: string
        focusedId?: string
        depth?: number
        playgroundEnabled?: boolean
    }>()

    const emit = defineEmits<{
        (e: "select", path: string): void
        (e: "open-split", path: string): void
        (e: "delete", path: string): void
        (e: "duplicate", path: string): void
        (e: "run", taskId: string): void
        (e: "add-at-path", parentPath: string, afterIndex: number, evt?: Event): void
        (e: "update-depends-on", itemPath: string, dependsOn: string[]): void
        (e: "reorder", parentPath: string, fromIndex: number, toIndex: number): void
    }>()

    const depth = computed(() => props.depth ?? 0)

    const displayBlock = computed(() => displayTaskOf(props.block))

    const validationIssues = inject(BLOCK_VALIDATION_ISSUES_INJECTION_KEY, undefined)
    const issues = computed<string[]>(() =>
        validationIssues?.value?.get(String(displayBlock.value.id ?? "")) ?? [],
    )

    const taskPath = computed(() => taskEditPathFor(props.path, props.block))

    const focused = computed(() => props.focusedId !== undefined && props.focusedId === (props.domId ?? String(displayBlock.value.id ?? "")))

    const expanded = ref(true)

    function toggle() {
        expanded.value = !expanded.value
    }

    const shortType = computed(() => {
        const type = String(displayBlock.value.type ?? "")
        const parts = type.split(".")
        return parts[parts.length - 1] ?? type
    })

    const flowableSuffix = computed(() => {
        return Object.keys(FLOWABLE_SUFFIX_MAP).find(suffix =>
            String(displayBlock.value.type ?? "").endsWith(`.${suffix}`),
        ) ?? null
    })

    const isSwitchTask = computed(() => flowableSuffix.value === "Switch")

    const branchKeys = computed<string[]>(() => {
        const suffix = flowableSuffix.value
        if (!suffix) return ["tasks"]
        const keys = FLOWABLE_SUFFIX_MAP[suffix] ?? ["tasks"]
        const result: string[] = []
        for (const key of keys) {
            if (key === "cases") {
                const casesObj = displayBlock.value.cases
                if (casesObj && typeof casesObj === "object" && !Array.isArray(casesObj)) {
                    for (const caseKey of Object.keys(casesObj as Record<string, unknown>)) {
                        result.push(`cases.${caseKey}`)
                    }
                }
            } else {
                result.push(key)
            }
        }
        if (isSwitchTask.value && !result.includes("defaults")) {
            result.push("defaults")
        }
        return result
    })

    interface Lane {
        name: string
        tasks: Record<string, unknown>[]
    }

    const lanes = computed<Lane[]>(() => {
        return branchKeys.value.map(laneName => {
            if (laneName.startsWith("cases.")) {
                const caseKey = laneName.slice("cases.".length)
                const casesObj = displayBlock.value.cases as Record<string, unknown> | undefined
                const caseArr = casesObj?.[caseKey]
                return {
                    name: laneName,
                    tasks: Array.isArray(caseArr) ? (caseArr as Record<string, unknown>[]) : [],
                }
            }
            const val = displayBlock.value[laneName]
            return {
                name: laneName,
                tasks: Array.isArray(val) ? (val as Record<string, unknown>[]) : [],
            }
        })
    })

    const totalNestedCount = computed(() => lanes.value.reduce((sum, l) => sum + l.tasks.length, 0))

    const headerAriaLabel = computed(() =>
        expanded.value
            ? t("block_editor.cluster_collapse_aria", {id: String(displayBlock.value.id ?? "")})
            : t("block_editor.cluster_expand_aria", {id: String(displayBlock.value.id ?? ""), count: totalNestedCount.value}),
    )

    function laneParentPath(laneName: string): string {
        if (laneName.startsWith("cases.")) {
            const caseKey = laneName.slice("cases.".length)
            return flowYamlUtils.appendKeyToPath(`${taskPath.value}.cases`, caseKey)
        }
        return `${taskPath.value}.${laneName}`
    }

    const newCaseKey = ref("")

    function addCase(evt?: Event) {
        const key = newCaseKey.value.trim()
        if (!key) return
        emit("add-at-path", flowYamlUtils.appendKeyToPath(`${props.path}.cases`, key), -1, evt)
        newCaseKey.value = ""
    }
</script>

<style scoped lang="scss">
    .flowable-cluster {
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-base);
        background: var(--ks-bg-surface);
        border-left: 3px solid var(--ks-border-strong);
        overflow: hidden;
    }

    .flowable-cluster:not(.flowable-cluster--expanded) {
        background: var(--ks-btn-secondary-bg-default);
        border-left-width: 1px;
        border-left-color: var(--ks-border-default);
    }

    .flowable-cluster--error {
        border-color: var(--ks-border-error);
        border-left-color: var(--ks-border-error);
    }

    .flowable-cluster-header {
        position: relative;
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        min-height: 3.25rem;
        padding: var(--ks-spacing-2) var(--ks-spacing-3);
        cursor: pointer;
        user-select: none;
        outline: none;
        transition: background-color 0.15s;

        &:hover {
            background: var(--ks-bg-hover);
        }

        &:focus-visible {
            outline: 2px solid var(--ks-border-focus);
            outline-offset: -2px;
        }
    }

    .flowable-cluster-chevron {
        font-size: 1rem;
        display: flex;
        flex-shrink: 0;
        color: var(--ks-icon-muted);
        transition: transform 0.15s;
    }

    .flowable-cluster-icon {
        flex-shrink: 0;
        width: var(--ks-icon-size-lg);
        height: var(--ks-icon-size-lg);
    }

    .flowable-cluster-id {
        font-size: var(--ks-font-size-sm);
        font-weight: 600;
        font-family: var(--ks-font-family-mono);
        color: var(--ks-text-primary);
        flex: 0 1 auto;
        min-width: 0;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .flowable-cluster-kind-tag {
        flex-shrink: 0;
        margin-left: auto;
    }

    .flowable-cluster-summary {
        font-size: var(--ks-font-size-xs);
        color: var(--ks-text-muted);
        flex-shrink: 0;
    }

    .flowable-cluster-actions {
        position: absolute;
        right: var(--ks-spacing-3);
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

        .flowable-cluster-header:hover &,
        .flowable-cluster-header:focus-within & {
            opacity: 1;
            pointer-events: auto;
        }
    }

    .flowable-cluster-action--danger:hover {
        color: var(--ks-text-error);
    }

    .flowable-cluster-body {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-3);
        padding: var(--ks-spacing-3);
        border-top: 1px solid var(--ks-border-default);
    }

    .flowable-cluster-add-case {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        padding-top: var(--ks-spacing-2);
        border-top: 1px dashed var(--ks-border-strong);
    }

    .flowable-cluster-case-input {
        flex: 1;
        max-width: 200px;
    }

    .flowable-cluster-add-case-btn {
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        background: transparent;
        border: 1px dashed var(--ks-border-strong);
        border-radius: var(--ks-radius-base);
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-sm);
        padding: var(--ks-spacing-1) var(--ks-spacing-2);
        cursor: pointer;
        transition: color 0.15s, border-color 0.15s;

        &:hover:not(:disabled) {
            color: var(--ks-text-primary);
            border-color: var(--ks-border-strong);
            background: var(--ks-btn-secondary-bg-hover);
        }

        &:focus-visible {
            outline: 2px solid var(--ks-border-focus);
            outline-offset: 1px;
        }

        &:disabled {
            opacity: 0.5;
            cursor: default;
        }
    }

    .flowable-cluster-add-icon {
        font-size: var(--ks-font-size-sm);
        display: flex;
    }

    .block-kbd-focused {
        box-shadow: 0 0 0 2px var(--ks-border-focus);
    }
</style>
