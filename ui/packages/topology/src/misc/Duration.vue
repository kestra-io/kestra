<template>
    <KsPopover
        v-model:visible="visible"
        :trigger="trigger"
        :enterable="true"
        :hideAfter="150"
        placement="bottom-start"
        :showArrow="false"
        :disabled="!hasHistory"
        :width="tier === 1 ? '20rem' : '22rem'"
        :popperStyle="tier === 1 ? TIER1_POPPER_STYLE : TIER2_POPPER_STYLE"
        @hide="onHide"
    >
        <template #reference>
            <button
                type="button"
                class="ks-duration-value"
                :disabled="!hasHistory"
                :aria-label="ariaLabel"
            >
                {{ triggerLabel }}
            </button>
        </template>
        <template v-if="hasHistory" #default>
            <div v-if="tier === 1" class="duration-card">
                <div class="duration-card-head">
                    <span class="duration-card-label">{{ $t('duration') }}</span>
                    <KsExecutionStatus v-if="lastState" size="small" :status="(lastState as ExecutionStatus)" />
                </div>
                <div class="duration-total" aria-live="off">
                    <template v-if="neverRan">
                        <span class="duration-total-empty">&mdash;</span>
                        <span class="duration-total-note">{{ $t('state_history.did_not_run') }}</span>
                    </template>
                    <template v-else>
                        {{ formatCardTotal(breakdown.total) }}
                        <span v-if="waitingToStart" class="duration-total-note">{{ $t('state_history.waiting_to_start') }}</span>
                        <span v-else-if="displayedAttemptCount" class="duration-total-note">{{ displayedAttemptCount }} {{ $t('attempts') }}</span>
                    </template>
                </div>
                <template v-if="!neverRan">
                    <div class="split-bar" aria-hidden="true">
                        <span
                            v-if="breakdown.queued > 0"
                            class="split-bar-seg split-bar-queued"
                            :style="{width: shareOf(breakdown.queued) + '%'}"
                        />
                        <span
                            v-if="breakdown.running > 0"
                            class="split-bar-seg split-bar-running"
                            :class="{'split-bar-running-live': isActivelyRunning}"
                            :style="{width: shareOf(breakdown.running) + '%'}"
                        />
                    </div>
                    <div class="split-rows">
                        <div v-if="breakdown.queued > 0" class="split-row">
                            <span class="split-key split-bar-queued" />
                            <span class="split-name">{{ $t('queued duration') }}</span>
                            <span class="split-value">{{ formatPhaseDuration(breakdown.queued) }}</span>
                            <span class="split-share">{{ shareLabel(breakdown.queued) }}</span>
                        </div>
                        <div v-if="breakdown.running > 0" class="split-row">
                            <span class="split-key split-bar-running" />
                            <span class="split-name">{{ $t('running duration') }}</span>
                            <span class="split-value">{{ formatPhaseDuration(breakdown.running) }}</span>
                            <span class="split-share">{{ shareLabel(breakdown.running) }}</span>
                        </div>
                    </div>
                </template>
                <div class="duration-card-foot">
                    <span class="duration-card-anchor">{{ anchorLabel }}</span>
                    <KsButton link :icon="History" @click="openHistory">
                        {{ $t('state_history.open') }}
                    </KsButton>
                </div>
            </div>
            <div v-else class="state-history">
                <div class="state-history-head">
                    <span>
                        <span class="state-history-title">{{ $t('state_history.title') }}</span>
                        <span class="state-history-date">{{ headerDate }}</span>
                    </span>
                    <KsIconButton :tooltip="$t('close')" placement="top" @click="closeHistory">
                        <Close />
                    </KsIconButton>
                </div>
                <KsScrollbar maxHeight="15rem" class="state-history-body">
                    <template v-for="row in timelineRows" :key="row.key">
                        <div v-if="row.type === 'attempt'" class="attempt-head" data-test="state-history-attempt-header">
                            {{ row.label }}
                        </div>
                        <div v-else-if="row.type === 'day'" class="sh-day" data-test="state-history-day-separator">
                            {{ row.label }}
                        </div>
                        <div v-else-if="row.type === 'gap'" class="sh-gap" data-test="state-history-gap">
                            <span class="sh-gap-value">{{ row.label }}</span>
                        </div>
                        <div v-else class="sh-item" data-test="state-history-item">
                            <span class="sh-dot" />
                            <span class="sh-time">{{ row.time }}</span>
                            <KsExecutionStatus size="small" :status="(row.state as ExecutionStatus)" />
                        </div>
                    </template>
                </KsScrollbar>
                <div class="state-history-foot">
                    <span class="state-history-total">
                        {{ $t('total_duration') }} <b>{{ formatCardTotal(breakdown.total) }}</b>
                    </span>
                    <KsButton link :icon="copied ? Check : ContentCopy" :tooltip="$t('state_history.copy')" @click="copyHistory">
                        {{ $t('copy') }}
                    </KsButton>
                </div>
            </div>
        </template>
    </KsPopover>
</template>

<script setup lang="ts">
    import {computed, onBeforeUnmount, onMounted, ref, watch} from "vue"
    import {useI18n} from "vue-i18n"
    import moment, {type Moment} from "moment-timezone"
    import {
        State,
        KsPopover,
        KsButton,
        KsIconButton,
        KsScrollbar,
        KsExecutionStatus,
        type ExecutionStatus,
    } from "@kestra-io/design-system"
    import History from "vue-material-design-icons/History.vue"
    import Close from "vue-material-design-icons/Close.vue"
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue"
    import Check from "vue-material-design-icons/Check.vue"
    import * as Utils from "../utils/utils"
    import {TIMEZONE_STORAGE_KEY} from "../utils/utils"
    import {computeDurationBreakdown} from "./durationBreakdown"

    const TIER1_POPPER_STYLE = {
        padding: "0.75rem",
        borderRadius: "var(--ks-radius-xs)",
        background: "var(--ks-bg-input)",
        border: "1px solid var(--ks-border-default)",
        boxShadow: "0 2px 6px var(--ks-shadow-element)",
    }

    const TIER2_POPPER_STYLE = {
        padding: "0",
        overflow: "hidden",
        borderRadius: "var(--ks-radius-xl)",
        background: "var(--ks-bg-elevated)",
        boxShadow: "0px 8px 24px 0px var(--ks-shadow-elevated)",
    }

    const props = withDefaults(defineProps<{
        histories?: {
            date: string | number | Moment;
            state: string;
        }[];
        interval?: number;
        /** Authoritative attempt count (e.g. `taskRun.attempts.length`). Falls back to a count
         *  derived from RETRYING transitions in `histories` when not provided. */
        attemptCount?: number;
        /** What this duration belongs to (e.g. a task id), used to disambiguate the trigger's
         *  aria-label when several are rendered on the same page (e.g. the Logs tab). */
        subject?: string;
    }>(), {
        histories: undefined,
        interval: 100,
        attemptCount: undefined,
        subject: undefined,
    })

    const {t} = useI18n()

    const normalizedHistories = computed(() =>
        (props.histories ?? []).map((h) => ({
            date: moment(h.date),
            state: h.state,
        })),
    )

    const filteredHistories = computed(() => {
        return normalizedHistories.value
            .filter((h) => h.date.isValid() && h.date && h.state)
            .sort((a, b) => a.date.valueOf() - b.date.valueOf())
    })

    const hasHistory = computed(() => filteredHistories.value.length > 0)

    const lastState = computed<string | undefined>(() =>
        hasHistory.value ? filteredHistories.value[filteredHistories.value.length - 1].state : undefined,
    )

    const now = ref(Date.now())
    let refreshHandler: ReturnType<typeof setInterval> | undefined

    const breakdown = computed(() => computeDurationBreakdown(
        filteredHistories.value.map((h) => ({date: h.date.valueOf(), state: h.state})),
        now.value,
    ))

    const isActivelyRunning = computed(() => breakdown.value.isRunning && lastState.value === State.RUNNING)
    const waitingToStart = computed(() => breakdown.value.isRunning && breakdown.value.running === 0)
    const neverRan = computed(() => hasHistory.value && !breakdown.value.isRunning && breakdown.value.total === 0)

    const derivedAttemptGroupCount = computed(() => {
        if (!hasHistory.value) return 0
        const boundaries = filteredHistories.value.filter((h, i) => i > 0 && h.state === "RETRYING").length
        return boundaries + 1
    })

    // The attempt count is only ever displayed when the caller supplies an authoritative value
    // (e.g. taskRun.attempts.length). Deriving it from RETRYING transitions instead would leak a
    // child task's retry count onto surfaces with no "attempts" concept at all, such as an
    // execution-level total. Grouping in the state history still uses the derived boundaries
    // below: it needs to know *where* attempts split, not whether to display a count.
    const displayedAttemptCount = computed(() => {
        return props.attemptCount && props.attemptCount > 1 ? props.attemptCount : undefined
    })

    const ariaLabel = computed(() => {
        if (!hasHistory.value) return t("no_history")
        return props.subject
            ? t("state_history.aria_open_for", {subject: props.subject})
            : t("state_history.aria_open")
    })

    function paint() {
        now.value = Date.now()
        if (!refreshHandler && breakdown.value.isRunning) {
            refreshHandler = setInterval(() => {
                now.value = Date.now()
                if (!breakdown.value.isRunning) {
                    cancel()
                }
            }, props.interval)
        }
    }

    function cancel() {
        if (refreshHandler) {
            clearInterval(refreshHandler)
            refreshHandler = undefined
        }
    }

    onMounted(paint)

    watch(
        normalizedHistories,
        (newValue, oldValue) => {
            if (newValue?.[0]?.date?.valueOf() !== oldValue?.[0]?.date?.valueOf()) {
                paint()
            }
        },
    )

    onBeforeUnmount(cancel)

    function formatDuration(ms: number, delimiter = ", "): string {
        const human = Utils.humanDuration(ms / 1000, {maxDecimalPoints: 2, units: ["h", "m", "s"], delimiter})
        const isBareSeconds = human.endsWith("s") && !human.endsWith("ms") && !human.includes(".")
        return isBareSeconds ? `${human.slice(0, -1)}.00s` : human
    }

    function formatPhaseDuration(ms: number): string {
        if (ms < 1000) {
            return Utils.humanDuration(ms / 1000, {maxDecimalPoints: 0, units: ["ms"]})
        }
        return Utils.humanDuration(ms / 1000, {maxDecimalPoints: 2, units: ["h", "m", "s"], delimiter: " "})
    }

    // The card's own headline, unlike triggerLabel (the persistent chip/column value, left
    // untouched to avoid reflowing the topology node or the Gantt duration column): a sub-second
    // total reads as "no time at all" in "h"/"m"/"s" units right next to a millisecond-precision
    // breakdown row, so it switches to plain milliseconds below 1s.
    function formatCardTotal(ms: number): string {
        if (ms > 0 && ms < 1000) {
            return Utils.humanDuration(ms / 1000, {maxDecimalPoints: 0, units: ["ms"]})
        }
        return formatDuration(ms, " ")
    }

    const triggerLabel = computed(() => {
        if (!hasHistory.value || neverRan.value) return "—"
        return formatDuration(breakdown.value.total)
    })

    function shareOf(part: number): number {
        if (breakdown.value.total <= 0) return 0
        return Math.min(100, (part / breakdown.value.total) * 100)
    }

    function shareLabel(part: number): string {
        if (breakdown.value.total <= 0) return "0%"
        if (part >= breakdown.value.total) return "100%"
        return `${((part / breakdown.value.total) * 100).toFixed(1)}%`
    }

    const timezone = computed(() => localStorage.getItem(TIMEZONE_STORAGE_KEY) ?? moment.tz.guess())

    function formatInTimezone(date: Moment, format: string): string {
        return moment(date).tz(timezone.value).format(format)
    }

    const anchorLabel = computed(() => {
        if (!hasHistory.value) return ""
        const first = filteredHistories.value[0]
        const last = filteredHistories.value[filteredHistories.value.length - 1]
        if (lastState.value === State.FAILED) {
            return t("state_history.failed_at", {time: formatInTimezone(last.date, "HH:mm:ss.SSS")})
        }
        if (lastState.value === State.SKIPPED) {
            return t("state_history.skipped_at", {time: formatInTimezone(last.date, "HH:mm:ss.SSS")})
        }
        if (waitingToStart.value) {
            return t("state_history.created_at", {time: formatInTimezone(first.date, "HH:mm:ss.SSS")})
        }
        return t("state_history.started_at", {time: formatInTimezone(first.date, "HH:mm:ss.SSS")})
    })

    const headerDate = computed(() => {
        if (!hasHistory.value) return ""
        const date = formatInTimezone(filteredHistories.value[0].date, "YYYY-MM-DD")
        return displayedAttemptCount.value ? `${date} · ${displayedAttemptCount.value} ${t("attempts")}` : date
    })

    interface TimelineRow {
        key: string;
        type: "attempt" | "day" | "item" | "gap";
        label?: string;
        time?: string;
        state?: string;
    }

    const timelineRows = computed<TimelineRow[]>(() => {
        const entries = filteredHistories.value
        const rows: TimelineRow[] = []
        // Grouping reflects the RETRYING boundaries actually found in this history, independent
        // of the authoritative attemptCount: the two can disagree (e.g. a truncated history), and
        // there is nothing meaningful to group when no boundary exists.
        const showAttempts = derivedAttemptGroupCount.value > 1
        // "Attempt N" is only ever a label the caller can back up with an authoritative count
        // (see displayedAttemptCount): without one, the boundary still separates the groups
        // visually, just with no unowned "Attempt" claim attached to it.
        const showAttemptLabels = displayedAttemptCount.value !== undefined
        let previousDay: string | null = null
        let groupIndex = 1

        if (showAttempts) {
            rows.push({key: "attempt-1", type: "attempt", label: showAttemptLabels ? t("state_history.attempt_n", {index: 1}) : undefined})
        }

        entries.forEach((entry, i) => {
            const isBoundary = showAttempts && i > 0 && entry.state === "RETRYING"
            if (isBoundary) {
                groupIndex++
                rows.push({key: `attempt-${groupIndex}`, type: "attempt", label: showAttemptLabels ? t("state_history.attempt_n", {index: groupIndex}) : undefined})
            }

            const day = formatInTimezone(entry.date, "YYYY-MM-DD")
            if (i > 0 && day !== previousDay) {
                rows.push({key: `day-${i}`, type: "day", label: day})
            }

            if (i > 0 && !isBoundary) {
                const gapMs = entry.date.valueOf() - entries[i - 1].date.valueOf()
                rows.push({key: `gap-${i}`, type: "gap", label: `+${formatPhaseDuration(gapMs)}`})
            }

            rows.push({
                key: `item-${i}`,
                type: "item",
                time: formatInTimezone(entry.date, "HH:mm:ss.SSS"),
                state: entry.state,
            })
            previousDay = day
        })

        return rows
    })

    const visible = ref(false)
    const tier = ref<1 | 2>(1)
    const trigger = computed(() => (tier.value === 1 ? "hover" : "click"))
    const copied = ref(false)

    function openHistory() {
        tier.value = 2
        visible.value = true
    }

    function closeHistory() {
        visible.value = false
    }

    function onHide() {
        tier.value = 1
    }

    function copyHistory() {
        const text = filteredHistories.value
            .map((h) => `${formatInTimezone(h.date, "YYYY-MM-DD HH:mm:ss.SSS")}  ${h.state}`)
            .join("\n")

        navigator.clipboard?.writeText(text).then(() => {
            copied.value = true
            setTimeout(() => {
                copied.value = false
            }, 2000)
        }).catch(() => { /* clipboard unavailable */ })
    }
</script>

<style scoped lang="scss">
    .ks-duration-value {
        appearance: none;
        padding: 0;
        border: none;
        background: transparent;
        color: inherit;
        font: inherit;
        font-variant-numeric: tabular-nums;
        cursor: pointer;
        border-radius: var(--ks-radius-xs);
        text-decoration: underline;
        text-decoration-style: dotted;
        text-underline-offset: 0.15em;
        text-decoration-color: color-mix(in srgb, currentColor, transparent 55%);

        &:hover {
            text-decoration-color: currentColor;
        }

        &:focus-visible {
            outline: 2px solid var(--ks-border-focus);
            outline-offset: 2px;
        }

        &:disabled {
            cursor: default;
            text-decoration: none;
        }
    }

    .duration-card {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-2);
        font-size: var(--ks-font-size-sm);
    }

    .duration-card-head {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--ks-spacing-2);
    }

    .duration-card-label {
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-xs);
        font-weight: var(--ks-font-weight-semibold);
        letter-spacing: 0.04em;
        text-transform: uppercase;
    }

    .duration-total {
        display: flex;
        align-items: baseline;
        gap: var(--ks-spacing-2);
        color: var(--ks-text-primary);
        font-size: var(--ks-font-size-2xl);
        font-weight: var(--ks-font-weight-semibold);
        font-variant-numeric: tabular-nums;
        line-height: var(--ks-line-height-tight);
    }

    .duration-total-note {
        font-size: var(--ks-font-size-xs);
        font-weight: var(--ks-font-weight-regular);
        color: var(--ks-text-secondary);
    }

    .duration-total-empty {
        color: var(--ks-text-muted);
    }

    .split-bar {
        display: flex;
        gap: var(--ks-spacing-px);
        height: var(--ks-spacing-2);
        border-radius: var(--ks-radius-xs);
        background: var(--ks-bg-tag);
        overflow: hidden;
    }

    .split-bar-seg {
        height: 100%;
        min-width: 3px;
    }

    .split-bar-queued {
        background: var(--ks-status-neutral);
    }

    .split-bar-running {
        background: var(--ks-status-running);
    }

    .split-bar-running-live {
        background-image: linear-gradient(45deg, var(--ks-bg-tag) 25%, transparent 25%, transparent 50%, var(--ks-bg-tag) 50%, var(--ks-bg-tag) 75%, transparent 75%);
        background-size: 0.625rem 0.625rem;
        animation: ks-duration-flow 1s linear infinite;
    }

    @keyframes ks-duration-flow {
        to {
            background-position: 0.625rem 0;
        }
    }

    .split-rows {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-1);
    }

    .split-row {
        display: grid;
        grid-template-columns: var(--ks-spacing-2) minmax(0, 1fr) auto auto;
        align-items: center;
        gap: var(--ks-spacing-2);
    }

    .split-key {
        width: var(--ks-spacing-2);
        height: var(--ks-spacing-2);
        border-radius: var(--ks-radius-xs);
    }

    .split-name {
        color: var(--ks-text-secondary);
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
    }

    .split-value {
        color: var(--ks-text-primary);
        font-weight: var(--ks-font-weight-medium);
        font-variant-numeric: tabular-nums;
        min-width: 4.75rem;
        text-align: right;
        white-space: nowrap;
    }

    .split-share {
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-xs);
        font-variant-numeric: tabular-nums;
        min-width: 2.5rem;
        text-align: right;
    }

    .duration-card-foot {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--ks-spacing-2);
        padding-top: var(--ks-spacing-2);
        border-top: var(--ks-border-block-secondary);
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-xs);
    }

    .duration-card-anchor {
        font-variant-numeric: tabular-nums;
    }

    .state-history {
        display: flex;
        flex-direction: column;
    }

    .state-history-head {
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        gap: var(--ks-spacing-2);
        padding: var(--ks-spacing-4) var(--ks-spacing-4) var(--ks-spacing-2);
    }

    .state-history-title {
        color: var(--ks-text-primary);
        font-size: var(--ks-font-size-lg);
        font-weight: var(--ks-font-weight-semibold);
        line-height: var(--ks-line-height-tight);
    }

    .state-history-date {
        display: block;
        margin-top: var(--ks-spacing-1);
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-xs);
        font-variant-numeric: tabular-nums;
    }

    .state-history-body {
        padding: var(--ks-spacing-2) var(--ks-spacing-4) var(--ks-spacing-4);
    }

    .attempt-head {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        margin: var(--ks-spacing-3) 0 var(--ks-spacing-2);
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-xs);
        font-weight: var(--ks-font-weight-semibold);

        &:first-child {
            margin-top: 0;
        }

        &::after {
            content: "";
            flex: 1 1 auto;
            height: 1px;
            background: var(--ks-border-subtle);
        }
    }

    .sh-day {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        margin: var(--ks-spacing-2) 0;
        color: var(--ks-text-muted);
        font-size: var(--ks-font-size-2xs);
        font-weight: var(--ks-font-weight-semibold);

        &::after {
            content: "";
            flex: 1 1 auto;
            height: 1px;
            background: var(--ks-border-subtle);
        }
    }

    .sh-item {
        position: relative;
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-3);
    }

    .sh-dot {
        flex: none;
        width: var(--ks-spacing-1);
        height: var(--ks-spacing-1);
        border-radius: 50%;
        background: var(--ks-text-primary);
    }

    .sh-time {
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-sm);
        font-variant-numeric: tabular-nums;
        min-width: 5.75rem;
    }

    .sh-gap {
        position: relative;
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        min-height: var(--ks-spacing-5);
        padding-left: calc(var(--ks-spacing-1) / 2);
    }

    .sh-gap::before {
        content: "";
        position: absolute;
        left: calc(var(--ks-spacing-1) / 2);
        top: 0;
        bottom: 0;
        transform: translateX(-50%);
        border-left: 1px dashed var(--ks-text-primary);
    }

    .sh-gap-value {
        margin-left: var(--ks-spacing-3);
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-xs);
        font-variant-numeric: tabular-nums;
    }

    .state-history-foot {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--ks-spacing-2);
        padding: var(--ks-spacing-2) var(--ks-spacing-4) var(--ks-spacing-3);
        border-top: var(--ks-border-block-secondary);
        font-size: var(--ks-font-size-sm);
    }

    .state-history-total {
        color: var(--ks-text-secondary);

        b {
            color: var(--ks-text-primary);
            font-weight: var(--ks-font-weight-semibold);
            font-variant-numeric: tabular-nums;
        }
    }
</style>
