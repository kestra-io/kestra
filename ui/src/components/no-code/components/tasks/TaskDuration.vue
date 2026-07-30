<template>
    <div class="task-duration">
        <div class="task-duration-rows">
            <div
                v-for="(segment, index) in segments"
                :key="index"
                class="task-duration-row"
            >
                <KsInputNumber
                    :modelValue="segment.value ?? undefined"
                    :min="0"
                    :controls="false"
                    placeholder="0"
                    class="task-duration-value"
                    @update:model-value="(value) => onValue(index, value)"
                />
                <KsSelect
                    :modelValue="segment.unit"
                    class="task-duration-unit"
                    @update:model-value="(value) => onUnit(index, String(value))"
                >
                    <KsOption
                        v-for="unit in availableUnits(index)"
                        :key="unit.key"
                        :value="unit.key"
                        :label="$t(`no_code.duration.units.${unit.label}`)"
                    />
                </KsSelect>
                <KsIconButton
                    v-if="segments.length > 1"
                    :tooltip="$t('delete')"
                    @click="removeSegment(index)"
                >
                    <Close />
                </KsIconButton>
            </div>
        </div>

        <button
            v-if="segments.length < UNITS.length"
            type="button"
            class="task-duration-add"
            @click="addSegment"
        >
            <Plus :size="16" />
            <span>{{ $t("no_code.duration.add_unit") }}</span>
        </button>

        <div class="task-duration-presets">
            <button
                v-for="preset in PRESETS"
                :key="preset.label"
                type="button"
                class="task-duration-preset"
                :class="{active: isActivePreset(preset)}"
                @click="applyPreset(preset)"
            >
                {{ preset.label }}
            </button>
        </div>

        <div v-if="humanText" class="task-duration-echo" :title="iso">
            <ClockOutline :size="14" />
            <span>{{ humanText }}</span>
        </div>
    </div>
</template>

<style scoped lang="scss">
.task-duration {
    display: flex;
    flex-direction: column;
    gap: var(--ks-spacing-3);
}

.task-duration-rows {
    display: flex;
    flex-direction: column;
    gap: var(--ks-spacing-2);
}

.task-duration-row {
    display: flex;
    align-items: center;
    gap: var(--ks-spacing-2);
}

.task-duration-value {
    width: 5rem;
    flex: none;
    font-variant-numeric: tabular-nums;
}

.task-duration-unit {
    flex: 1;
    max-width: 14rem;
}

.task-duration-add {
    display: inline-flex;
    align-items: center;
    gap: var(--ks-spacing-2);
    align-self: flex-start;
    min-height: 2.25rem;
    padding: 0 var(--ks-spacing-3);
    background: transparent;
    border: 1px dashed var(--ks-border-strong);
    border-radius: var(--ks-radius-base);
    color: var(--ks-text-secondary);
    font-family: inherit;
    font-size: var(--ks-font-size-sm);
    cursor: pointer;
    transition: border-color 0.12s ease, color 0.12s ease, background-color 0.12s ease, scale 0.1s ease;
}

.task-duration-add:hover {
    border-color: var(--ks-border-strong);
    color: var(--ks-text-primary);
    background: var(--ks-btn-secondary-bg-hover);
}

.task-duration-add:active {
    scale: 0.98;
}

.task-duration-add:focus-visible {
    outline: 2px solid var(--ks-border-focus);
    outline-offset: 1px;
}

.task-duration-presets {
    display: flex;
    flex-wrap: wrap;
    gap: var(--ks-spacing-1);
}

.task-duration-preset {
    display: inline-flex;
    align-items: center;
    min-height: 1.875rem;
    padding: 0 var(--ks-spacing-3);
    background: var(--ks-bg-tag-inactive);
    border: 1px solid var(--ks-border-subtle);
    border-radius: var(--ks-radius-base);
    color: var(--ks-text-secondary);
    font-family: var(--ks-font-family-mono);
    font-size: var(--ks-font-size-xs);
    cursor: pointer;
    transition: border-color 0.12s ease, color 0.12s ease, background-color 0.12s ease, scale 0.1s ease;
}

.task-duration-preset:hover {
    border-color: var(--ks-border-strong);
    color: var(--ks-text-primary);
    background: var(--ks-btn-secondary-bg-hover);
}

.task-duration-preset:active {
    scale: 0.96;
}

.task-duration-preset:focus-visible {
    outline: 2px solid var(--ks-border-focus);
    outline-offset: 1px;
}

.task-duration-preset.active {
    border-color: var(--ks-text-link);
    color: var(--ks-text-link);
    background: var(--ks-bg-tag-hover);
}

.task-duration-echo {
    display: flex;
    align-items: center;
    gap: var(--ks-spacing-1);
    color: var(--ks-text-muted);
    font-size: var(--ks-font-size-sm);
    font-variant-numeric: tabular-nums;
}

.task-duration-echo span {
    color: var(--ks-text-secondary);
}
</style>

<script setup lang="ts">
    import {ref, computed, watch} from "vue"
    import Close from "vue-material-design-icons/Close.vue"
    import Plus from "vue-material-design-icons/Plus.vue"
    import ClockOutline from "vue-material-design-icons/ClockOutline.vue"
    import {durationUtils} from "@kestra-io/design-system"

    interface Segment {
        value: number | null;
        unit: string;
    }

    const UNITS = [
        {key: "w", label: "week", iso: "W"},
        {key: "d", label: "day", iso: "D"},
        {key: "h", label: "hour", iso: "H"},
        {key: "m", label: "minute", iso: "M"},
        {key: "s", label: "second", iso: "S"},
    ] as const

    const PRESETS = [
        {label: "30s", value: 30, unit: "s"},
        {label: "1m", value: 1, unit: "m"},
        {label: "5m", value: 5, unit: "m"},
        {label: "15m", value: 15, unit: "m"},
        {label: "1h", value: 1, unit: "h"},
        {label: "6h", value: 6, unit: "h"},
        {label: "1d", value: 1, unit: "d"},
    ]

    const props = defineProps<{modelValue?: string}>()

    const emit = defineEmits<{(e: "update:modelValue", value: string | undefined): void}>()

    const segments = ref<Segment[]>([])
    const localEdit = ref(false)

    function parse(value?: string): Segment[] {
        const num = "(-?\\d+(?:\\.\\d+)?)"
        const match = value?.match(new RegExp(`^-?P(?:${num}W)?(?:${num}D)?(?:T(?:${num}H)?(?:${num}M)?(?:${num}S)?)?$`, "i"))
        if (!match) return [{value: null, unit: "m"}]
        const units: [string | undefined, string][] = [
            [match[1], "w"], [match[2], "d"], [match[3], "h"], [match[4], "m"], [match[5], "s"],
        ]
        const parsed = units
            .filter(([raw]) => raw !== undefined)
            .map(([raw, unit]) => ({value: parseFloat(raw as string), unit}))
        return parsed.length ? parsed : [{value: null, unit: "m"}]
    }

    watch(() => props.modelValue, (value) => {
        if (localEdit.value) {
            localEdit.value = false
            return
        }
        segments.value = parse(value)
    }, {immediate: true})

    function build(input: Segment[]): string | undefined {
        const totals: Record<string, number> = {w: 0, d: 0, h: 0, m: 0, s: 0}
        for (const segment of input) {
            if (segment.value && segment.value > 0) {
                totals[segment.unit] += Math.round(segment.value)
            }
        }
        let date = ""
        if (totals.w) date += `${totals.w}W`
        if (totals.d) date += `${totals.d}D`
        let time = ""
        if (totals.h) time += `${totals.h}H`
        if (totals.m) time += `${totals.m}M`
        if (totals.s) time += `${totals.s}S`
        if (!date && !time) return undefined
        return `P${date}${time ? `T${time}` : ""}`
    }

    const iso = computed(() => build(segments.value))

    const humanText = computed(() => {
        const value = iso.value
        return value ? durationUtils.humanDuration(value) : ""
    })

    function emitChange() {
        localEdit.value = true
        emit("update:modelValue", build(segments.value))
    }

    function onValue(index: number, value: number | null | undefined) {
        segments.value[index].value = value == null ? null : Number(value)
        emitChange()
    }

    function onUnit(index: number, unit: string) {
        segments.value[index].unit = unit
        emitChange()
    }

    function availableUnits(index: number) {
        const usedByOthers = new Set(
            segments.value.filter((_, position) => position !== index).map((segment) => segment.unit),
        )
        return UNITS.filter((unit) => !usedByOthers.has(unit.key))
    }

    function addSegment() {
        const used = new Set(segments.value.map((segment) => segment.unit))
        const next = UNITS.find((unit) => !used.has(unit.key))?.key ?? "s"
        segments.value.push({value: null, unit: next})
    }

    function removeSegment(index: number) {
        segments.value.splice(index, 1)
        emitChange()
    }

    function applyPreset(preset: {value: number; unit: string}) {
        segments.value = [{value: preset.value, unit: preset.unit}]
        emitChange()
    }

    function isActivePreset(preset: {value: number; unit: string}) {
        return segments.value.length === 1
            && segments.value[0].value === preset.value
            && segments.value[0].unit === preset.unit
    }
</script>
