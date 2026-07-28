<template>
    <div class="legend" :class="{center}">
        <span
            v-for="item in visible"
            :key="item.label"
            class="item"
            :class="{off: toggledOff.has(item.label)}"
            @click="toggle(item.label)"
        >
            <span :style="swatchStyle(item.color)" />
            {{ displayLabel(item.label) }} {{ item.count }}
        </span>

        <KsTooltip v-if="hidden.length" placement="top">
            <template #content>
                <div style="display:flex; flex-direction:column; font-size:var(--ks-font-size-2xs); color:var(--ks-text-secondary); font-variant-numeric:tabular-nums;">
                    <span
                        v-for="item in hidden"
                        :key="item.label"
                        :style="{display:'flex', alignItems:'center', gap:'6px', whiteSpace:'nowrap', cursor:'pointer', opacity: toggledOff.has(item.label) ? 0.4 : 1}"
                        @click="toggle(item.label)"
                    >
                        <span :style="swatchStyle(item.color)" />
                        <span style="flex:1;">{{ displayLabel(item.label) }}</span>
                        <span>{{ item.count }}</span>
                    </span>
                </div>
            </template>
            <span class="ellipsis" tabindex="0" aria-label="Show all statuses">⋯</span>
        </KsTooltip>

        <span
            v-if="durationLabel"
            class="item"
            :class="{off: toggledOff.has(durationLabel)}"
            @click="toggle(durationLabel)"
        >
            <span class="line" />
            {{ displayLabel(durationLabel) }}
        </span>
    </div>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue"
    import type {EChartsType} from "echarts/core"
    import {KsTooltip} from "@kestra-io/design-system"

    interface ChartLegendItem {
        label: string;
        color: string;
        count: number;
    }

    const props = withDefaults(defineProps<{
        items: ChartLegendItem[];
        maxVisible?: number;
        durationLabel?: string;
        center?: boolean;
        chart?: {getEchartsInstance: () => EChartsType | null} | null;
    }>(), {
        maxVisible: 5,
        durationLabel: undefined,
        center: false,
        chart: null,
    })

    const emit = defineEmits<{toggle: [name: string]}>()

    const aggregated = computed<ChartLegendItem[]>(() => {
        const byLabel = new Map<string, ChartLegendItem>()
        for (const item of props.items) {
            const entry = byLabel.get(item.label) ?? {label: item.label, color: item.color, count: 0}
            entry.count += item.count || 0
            byLabel.set(item.label, entry)
        }
        return [...byLabel.values()].sort((a, b) => b.count - a.count)
    })

    const visible = computed(() => aggregated.value.slice(0, props.maxVisible))
    const hidden = computed(() => aggregated.value.slice(props.maxVisible))

    const toggledOff = ref(new Set<string>())

    function toggle(name: string) {
        const next = new Set(toggledOff.value)
        if (next.has(name)) next.delete(name)
        else next.add(name)
        toggledOff.value = next
        props.chart?.getEchartsInstance?.()?.dispatchAction({type: "legendToggleSelect", name})
        emit("toggle", name)
    }

    const displayLabel = (label: string) =>
        label.replace(/\w\S*/g, (word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())

    const swatchStyle = (color: string) => ({
        width: "10px",
        height: "10px",
        borderRadius: "2px",
        backgroundColor: color,
        flexShrink: 0,
        display: "inline-block",
    })
</script>

<style scoped lang="scss">
    .legend {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: var(--ks-spacing-3);
        margin-bottom: var(--ks-spacing-2);
        font-size: var(--ks-font-size-2xs);
        color: var(--ks-text-secondary);

        &.center {
            justify-content: center;
        }

        .item {
            display: inline-flex;
            align-items: center;
            gap: var(--ks-spacing-2);
            white-space: nowrap;
            cursor: pointer;
            transition: opacity var(--ks-duration-fast) ease;

            &.off {
                opacity: 0.4;
            }
        }

        .line {
            width: 14px;
            height: 2px;
            flex-shrink: 0;
            border-radius: var(--ks-radius-sm);
            background: var(--ks-chart-duration);
        }

        .ellipsis {
            display: inline-flex;
            align-items: center;
            line-height: 1;
            color: var(--ks-text-primary);
            cursor: pointer;
        }
    }
</style>
