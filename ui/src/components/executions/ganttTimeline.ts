import {inject, type InjectionKey, type Ref} from "vue"
import {State, durationUtils} from "@kestra-io/design-system"

export interface GanttTimeline {
    start: Ref<number>;
    delta: Readonly<Ref<number>>;
}

export const GANTT_TIMELINE: InjectionKey<GanttTimeline> = Symbol("ganttTimeline")

export interface StateHistory {
    state: string;
    date: string;
}

const MIN_BAR_PERCENT = 3
const COLORS = State.color()

const ts = (date: string | Date): number => new Date(date).getTime()

export function formatGanttDuration(ms: number): string {
    return durationUtils.humanDuration(ms / 1000, {maxDecimalPoints: 2, units: ["h", "m", "s"], delimiter: " "})
}

export function clampedBarStyle(startPercent: number, widthPercent: number, background: string): Record<string, string> {
    const width = Math.max(widthPercent, MIN_BAR_PERCENT)
    return {
        left: `${Math.max(0, Math.min(startPercent, 100 - width))}%`,
        width: `${width}%`,
        background,
    }
}

export function useGanttTimeline() {
    const timeline = inject(GANTT_TIMELINE, undefined)

    function offset(histories: StateHistory[]): string {
        if (!timeline || histories.length === 0) return ""
        return `+${formatGanttDuration(Math.max(0, ts(histories[0].date) - timeline.start.value))}`
    }

    function barStyle(histories: StateHistory[], current: string): Record<string, string> {
        if (!timeline || histories.length === 0) return {display: "none"}
        const delta = timeline.delta.value
        if (!isFinite(delta) || delta <= 0) return {display: "none"}

        const runningEntry = histories.find(h => h.state === State.RUNNING)
        const barStartTs = ts((runningEntry ?? histories[0]).date)
        const stopTs = State.isRunning(current) ? Date.now() : ts(histories[histories.length - 1].date)

        return clampedBarStyle(
            ((barStartTs - timeline.start.value) / delta) * 100,
            ((stopTs - barStartTs) / delta) * 100,
            COLORS[current],
        )
    }

    return {offset, barStyle}
}
