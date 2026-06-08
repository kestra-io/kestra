import type {Component} from "vue"
import CheckCircleOutline from "vue-material-design-icons/CheckCircleOutline.vue"
import CloseCircleOutline from "vue-material-design-icons/CloseCircleOutline.vue"
import SkipNextCircleOutline from "vue-material-design-icons/SkipNextCircleOutline.vue"
import ProgressClock from "vue-material-design-icons/ProgressClock.vue"
import RotatingDots from "../assets/icons/RotatingDots.vue"

export interface StatusStyle {
    icon: Component;
    textVar: string;
    bg?: string;
    border?: string;
    dimIcon?: boolean;
    label?: string;
}

const SUCCESS: StatusStyle = {
    icon: CheckCircleOutline,
    textVar: "--ks-text-success",
    bg: "color-mix(in srgb, var(--ks-status-success) 10%, var(--ks-bg-surface))",
    border: "var(--ks-border-success)",
}

const ERROR: StatusStyle = {
    icon: CloseCircleOutline,
    textVar: "--ks-text-error",
    bg: "var(--ks-topology-bg-errors)",
    border: "var(--ks-topology-border-errors)",
}

const RUNNING: StatusStyle = {
    icon: RotatingDots,
    textVar: "--ks-status-running",
    bg: "var(--ks-topology-bg-running)",
    border: "var(--ks-topology-border-running)",
}

const SKIPPED: StatusStyle = {
    icon: SkipNextCircleOutline,
    textVar: "--ks-status-neutral",
    dimIcon: true,
    label: "skipped", // i18n key, translated at render
}

// Fallback for executed states not covered by the Figma design.
const NEUTRAL: StatusStyle = {
    icon: ProgressClock,
    textVar: "--ks-status-neutral",
}

const STATUS_STYLES: Record<string, StatusStyle> = {
    success: SUCCESS,
    failed: ERROR,
    killed: ERROR,
    running: RUNNING,
    killing: RUNNING,
    skipped: SKIPPED,
    cancelled: SKIPPED,
}

export function getStatusStyle(state?: string | null): StatusStyle | undefined {
    if (!state) return undefined
    return STATUS_STYLES[state.toLowerCase()] ?? NEUTRAL
}
