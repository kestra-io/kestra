<template>
    <div class="timeline-toolbar">
        <div class="toolbar-controls">
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

            <!-- The range-pill button is also the popover's own reactive trigger (its class and
                 label change on every zoom/pan), so it can't be nested inside KsPopover's #reference
                 slot without a second, independent Trigger fighting the same DOM node for the merge
                 (the ElOnlyChild/ElPopperTrigger composition bug fixed for TimelineBar.vue's hover
                 tooltip). Point the popover at it via virtualRef/virtualTriggering instead. -->
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
    import {MAX_RANGE_MS, MIN_RANGE_MS} from "../../../composables/useTimelineRange"

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
    // Mirrors DateFilter.vue's own Relative/Absolute toggle: initialized once from the current range,
    // then left to the user so switching tabs doesn't fight their choice on every prop change.
    const selectedMode = ref<"REL" | "ABS">(props.activePreset !== undefined ? "REL" : "ABS")

    // dateUtils.dateFilter() reads getCurrentInstance() internally, which is only set while
    // Vue is synchronously rendering this component. A computed's getter can be re-invoked by
    // Vue's reactivity scheduler outside of any render pass (e.g. flushJobs refreshing a dirty
    // computed before the owning component's render job runs), where getCurrentInstance() is
    // undefined — so this must stay a plain function called from the template, not a computed.
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

    // The slider's track represents the widest window a user can zoom out to (MAX_RANGE_MS, the
    // same cap useTimelineRange's zoom() already enforces), anchored to now — widened further only
    // if the current selection already reaches outside it (e.g. an absolute range picked further back).
    const domainEndMs = computed(() => Math.max(Date.now(), props.rangeEndMs))
    const domainStartMs = computed(() => Math.min(domainEndMs.value - MAX_RANGE_MS, props.rangeStartMs))

    // Memoized so the array reference is stable across unrelated re-renders: KsRangeSlider's
    // defineModel() treats a new reference as an external change and would snap an in-progress
    // drag back to it (see useModel's watchSyncEffect in Vue's runtime-core).
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
    gap: var(--ks-spacing-2);
    padding: var(--ks-spacing-2) var(--ks-spacing-4);
    border-bottom: 1px solid var(--ks-border-default);
    background: var(--ks-bg-surface);
}

.toolbar-controls {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: var(--ks-spacing-3);
}

.range-slider {
    width: 100%;
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
