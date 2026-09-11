<template>
    <div class="toolbar">
        <KsTabs
            v-model="layoutMode"
            type="segmented"
            class="layout-toggle"
        >
            <KsTabPane
                name="force"
                :label="$t('dependency.dag.force')"
            />
            <KsTabPane
                name="dag"
                :label="$t('dependency.dag.layered')"
            />
        </KsTabs>

        <KsSelect
            v-model="groupField"
            class="group-select"
            size="small"
            clearable
            :placeholder="$t('dependency.dag.group_by')"
        >
            <KsOption :label="$t('dependency.dag.group_none')" value="" />
            <KsOption
                v-for="field in groupFields"
                :key="field.key"
                :label="`${field.label} (${field.groups})`"
                :value="field.key"
                :disabled="!field.usable"
            />
        </KsSelect>

        <GroupPicker
            v-if="groupChips.length"
            :groups="groupChips"
            :activeGroup="activeGroup"
            @preview="emit('preview', $event)"
            @toggle="emit('toggle', $event)"
        />

        <KsText
            v-if="summaryTokens.length"
            size="small"
            class="summary"
        >
            <template
                v-for="(token, index) in summaryTokens"
                :key="index"
            >
                <strong v-if="token.bold">{{ token.text }}</strong>
                <template v-else>{{ token.text }}</template>
            </template>
        </KsText>
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useI18n} from "vue-i18n"
    import GroupPicker from "./GroupPicker.vue"
    import type {LayoutMode} from "../../composables/useDependencies"
    import type {GroupField, GroupChip} from "../../composables/useDagGrouping"
    import {normalizeStatus, compactAge} from "../../utils/assetStatus"
    import {ASSET} from "../../utils/types"
    import type {Node} from "../../utils/types"

    const props = defineProps<{
        nodes: Node[];
        groupFields: GroupField[];
        groupChips: GroupChip[];
        activeGroup?: string;
    }>()

    const emit = defineEmits<{
        preview: [key: string | undefined];
        toggle: [key: string];
    }>()

    const layoutMode = defineModel<LayoutMode>("layoutMode", {required: true})
    const groupField = defineModel<string>("groupField", {required: true})

    const {t} = useI18n({useScope: "global"})

    const assets = computed(() => props.nodes
        .filter((node) => node.metadata.subtype === ASSET)
        .map((node) => node.metadata as {status?: string; updated?: string}))

    const summaryParts = computed<[key: string, value: string | number][]>(() => {
        if (!assets.value.length) {
            return []
        }

        const counts = {fresh: 0, stale: 0, failed: 0, unknown: 0}
        assets.value.forEach((asset) => counts[normalizeStatus(asset.status)]++)

        const updates = assets.value
            .map((asset) => Date.parse(asset.updated ?? ""))
            .filter((epoch) => !Number.isNaN(epoch))
        const lastRun = updates.length ? compactAge(new Date(Math.max(...updates)).toISOString()) : undefined

        const parts: [key: string, value: string | number][] = []
        if (counts.failed) {
            parts.push(["dependency.dag.summary.failed", counts.failed])
        }
        if (counts.stale) {
            parts.push(["dependency.dag.summary.issues", counts.stale])
        }
        if (!parts.length && counts.fresh === assets.value.length) {
            parts.push(["dependency.dag.summary.fresh", counts.fresh])
        }
        if (counts.unknown) {
            parts.push(["dependency.dag.summary.unknown", counts.unknown])
        }
        if (lastRun) {
            parts.push(["dependency.dag.summary.last_run", lastRun])
        }
        return parts
    })

    const summaryTokens = computed(() => summaryParts.value.flatMap(([key, value], index) => {
        const text = t(key, {n: value, ago: value})
        const bold = String(value)
        const start = text.indexOf(bold)
        const tokens = start === -1
            ? [{text, bold: false}]
            : [
                {text: text.slice(0, start), bold: false},
                {text: bold, bold: true},
                {text: text.slice(start + bold.length), bold: false},
            ]
        return [...(index ? [{text: " · ", bold: false}] : []), ...tokens]
            .filter((token) => token.text)
    }))
</script>

<style scoped lang="scss">
    .toolbar {
        display: flex;
        align-items: center;
        flex-wrap: wrap;
        gap: var(--ks-spacing-2);
        padding: var(--ks-spacing-2);

        .layout-toggle {
            flex: 0 0 auto;
        }

        .group-select {
            flex: 0 1 11rem;
            min-width: 0;
        }

        .summary {
            flex: 0 1 auto;
            min-width: 12rem;
            margin-left: auto;
            color: var(--ks-text-secondary);
            text-align: right;
        }
    }
</style>
