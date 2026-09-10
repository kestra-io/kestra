<template>
    <div class="timeline-toolbar">
        <KsSelect
            class="range-select"
            :modelValue="activePreset"
            :placeholder="$t('datepicker.custom')"
            @change="(value: string) => emit('apply-preset', value)"
        >
            <KsOption v-for="preset in presets" :key="preset.value" :value="preset.value" :label="$t(preset.labelKey)" />
        </KsSelect>

        <KsDatePicker
            class="range-picker"
            :modelValue="[new Date(rangeStartMs), new Date(rangeEndMs)]"
            type="datetimerange"
            :startPlaceholder="$t('start date')"
            :endPlaceholder="$t('end date')"
            @update:modelValue="onCustomRange"
        />

        <div class="zoom-controls">
            <KsIconButton :tooltip="$t('executionsTimeline.toolbar.panBack')" placement="top" @click="emit('pan', -0.5)">
                <ChevronLeft />
            </KsIconButton>
            <KsIconButton :tooltip="$t('executionsTimeline.toolbar.zoomOut')" placement="top" @click="emit('zoom', 2)">
                <MagnifyMinusOutline />
            </KsIconButton>
            <KsIconButton :tooltip="$t('executionsTimeline.toolbar.zoomIn')" placement="top" @click="emit('zoom', 0.5)">
                <MagnifyPlusOutline />
            </KsIconButton>
            <KsIconButton :tooltip="$t('executionsTimeline.toolbar.panForward')" placement="top" @click="emit('pan', 0.5)">
                <ChevronRight />
            </KsIconButton>
            <KsButton :icon="Crosshairs" size="small" @click="emit('now')">
                {{ $t("now") }}
            </KsButton>
            <KsIconButton
                :tooltip="expanded ? $t('collapse') : $t('expand')"
                placement="top"
                :aria-pressed="expanded"
                @click="emit('update:expanded', !expanded)"
            >
                <ArrowCollapseAll v-if="expanded" />
                <ArrowExpandAll v-else />
            </KsIconButton>
        </div>
    </div>
</template>

<script setup lang="ts">
    import ChevronLeft from "vue-material-design-icons/ChevronLeft.vue"
    import ChevronRight from "vue-material-design-icons/ChevronRight.vue"
    import MagnifyPlusOutline from "vue-material-design-icons/MagnifyPlusOutline.vue"
    import MagnifyMinusOutline from "vue-material-design-icons/MagnifyMinusOutline.vue"
    import Crosshairs from "vue-material-design-icons/Crosshairs.vue"
    import ArrowExpandAll from "vue-material-design-icons/ArrowExpandAll.vue"
    import ArrowCollapseAll from "vue-material-design-icons/ArrowCollapseAll.vue"
    import {TIMELINE_RANGE_PRESETS} from "../../../composables/useTimelineRange"

    defineProps<{
        rangeStartMs: number;
        rangeEndMs: number;
        activePreset?: string;
        expanded: boolean;
    }>()

    const emit = defineEmits<{
        "apply-preset": [value: string];
        "zoom": [factor: number];
        "pan": [fractionOfSpan: number];
        "now": [];
        "update:expanded": [value: boolean];
        "custom-range": [range: {startMs: number; endMs: number}];
    }>()

    const presets = TIMELINE_RANGE_PRESETS

    function onCustomRange(value: [Date, Date] | null) {
        if (!value?.[0] || !value?.[1]) return
        emit("custom-range", {startMs: value[0].getTime(), endMs: value[1].getTime()})
    }
</script>

<style scoped lang="scss">
.timeline-toolbar {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: var(--ks-spacing-3);
    padding: var(--ks-spacing-2) var(--ks-spacing-4);
    border-bottom: 1px solid var(--ks-border-default);
    background: var(--ks-bg-surface);
}

.range-select {
    width: 10rem;
}

.range-picker {
    width: 20rem;
}

.zoom-controls {
    display: flex;
    align-items: center;
    gap: var(--ks-spacing-1);
    margin-left: auto;
}
</style>
