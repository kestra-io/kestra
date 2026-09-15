<template>
    <div class="timeline-toolbar">
        <div class="primary-controls">
            <KsRangeSlider
                class="range-slider"
                :min="domainStartMs"
                :max="domainEndMs"
                :minRange="MIN_RANGE_MS"
                :modelValue="sliderRange"
                :formatValue="formatSliderValue"
                :startLabel="$t('executionsTimeline.toolbar.rangeSliderStart')"
                :endLabel="$t('executionsTimeline.toolbar.rangeSliderEnd')"
                @change="onSliderChange"
            />
        </div>

        <div class="secondary-controls">
            <KsButton
                ref="rangePillRef"
                class="range-pill"
                :class="{'is-active': rangePickerVisible}"
                :icon="CalendarRange"
                size="small"
                :aria-label="$t('executionsTimeline.toolbar.rangePicker')"
            >
                {{ readout() }}
            </KsButton>

            <KsPopover
                v-model:visible="rangePickerVisible"
                trigger="click"
                placement="bottom-start"
                :width="320"
                :showArrow="false"
                :virtualRef="rangePillRef"
                virtualTriggering
            >
                <div class="range-picker">
                    <KsRadioGroup v-model="selectedMode" class="range-picker-mode">
                        <KsRadioButton value="REL">
                            {{ $t("relative") }}
                        </KsRadioButton>
                        <KsRadioButton value="ABS">
                            {{ $t("absolute") }}
                        </KsRadioButton>
                    </KsRadioGroup>

                    <TimeSelect
                        v-if="selectedMode === 'REL'"
                        allowCustom
                        :timeRange="activePreset"
                        @update:modelValue="onRelativeChange"
                    />
                    <DateRange
                        v-else
                        :startDate="new Date(rangeStartMs).toISOString()"
                        :endDate="new Date(rangeEndMs).toISOString()"
                        @update:modelValue="onAbsoluteChange"
                    />
                </div>
            </KsPopover>

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
    </div>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue"
    import {useI18n} from "vue-i18n"
    import CalendarRange from "vue-material-design-icons/CalendarRange.vue"
    import ChevronLeft from "vue-material-design-icons/ChevronLeft.vue"
    import ChevronRight from "vue-material-design-icons/ChevronRight.vue"
    import MagnifyPlusOutline from "vue-material-design-icons/MagnifyPlusOutline.vue"
    import MagnifyMinusOutline from "vue-material-design-icons/MagnifyMinusOutline.vue"
    import Crosshairs from "vue-material-design-icons/Crosshairs.vue"
    import ArrowExpandAll from "vue-material-design-icons/ArrowExpandAll.vue"
    import ArrowCollapseAll from "vue-material-design-icons/ArrowCollapseAll.vue"
    import {dateUtils, durationUtils, KsButton} from "@kestra-io/design-system"
    import TimeSelect from "../date-select/TimeSelect.vue"
    import DateRange from "../../layout/DateRange.vue"
    import {MIN_RANGE_MS, computeSliderDomain} from "../../../composables/useTimelineRange"

    const props = defineProps<{
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

    const {t} = useI18n()

    const rangePickerVisible = ref(false)
    const rangePillRef = ref<InstanceType<typeof KsButton> | null>(null)
    const selectedMode = ref<"REL" | "ABS">(props.activePreset !== undefined ? "REL" : "ABS")

    // dateUtils.dateFilter() needs getCurrentInstance(), so this must stay a template-called function, not a computed.
    function readout() {
        return t("executionsTimeline.toolbar.range", {
            start: dateUtils.dateFilter(new Date(props.rangeStartMs).toISOString(), "lll"),
            end: dateUtils.dateFilter(new Date(props.rangeEndMs).toISOString(), "lll"),
            duration: durationUtils.humanDuration((props.rangeEndMs - props.rangeStartMs) / 1000),
        })
    }

    function onRelativeChange({timeRange}: {timeRange?: string}) {
        if (timeRange) emit("apply-preset", timeRange)
    }

    function onAbsoluteChange({startDate, endDate}: {startDate?: string; endDate?: string}) {
        if (!startDate || !endDate) return
        emit("custom-range", {startMs: Date.parse(startDate), endMs: Date.parse(endDate)})
    }

    const sliderDomain = computed(() => computeSliderDomain(props.rangeStartMs, props.rangeEndMs))
    const domainStartMs = computed(() => sliderDomain.value[0])
    const domainEndMs = computed(() => sliderDomain.value[1])

    // Stable array reference: KsRangeSlider's defineModel() treats a new one as an external change mid-drag.
    const sliderRange = computed<[number, number]>(() => [props.rangeStartMs, props.rangeEndMs])

    function formatSliderValue(ms: number) {
        return dateUtils.dateFilter(new Date(ms).toISOString(), "lll")
    }

    function onSliderChange([startMs, endMs]: [number, number]) {
        emit("custom-range", {startMs, endMs})
    }
</script>

<style scoped lang="scss">
.timeline-toolbar {
    display: flex;
    flex-direction: column;
    gap: var(--ks-spacing-3);
    padding: var(--ks-spacing-3) var(--ks-spacing-4);
    border-bottom: 1px solid var(--ks-border-default);
    background: var(--ks-bg-surface);
}

.primary-controls {
    display: flex;
    align-items: center;
    padding-block: var(--ks-spacing-1);
}

.range-slider {
    width: 100%;
}

.secondary-controls {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: var(--ks-spacing-2);
}

.range-pill {
    font-variant-numeric: tabular-nums;
}

.range-pill.is-active {
    color: var(--ks-text-primary);
    background: var(--ks-btn-secondary-bg-active);
    border-color: var(--ks-btn-secondary-border-active);
}

.range-picker {
    display: flex;
    flex-direction: column;
    gap: var(--ks-spacing-3);
}

.range-picker-mode {
    align-self: flex-start;
}

.zoom-controls {
    display: flex;
    align-items: center;
    gap: var(--ks-spacing-1);
    margin-left: auto;
}
</style>
