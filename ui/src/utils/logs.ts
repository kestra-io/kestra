import {LOG_LEVELS} from "@kestra-io/design-system"
import {cssVar} from "@kestra-io/design-system"
import type {Log} from "../stores/logs"

export type LevelKey = typeof LOG_LEVELS[number];

export const STRUCTURED_PARSE_LIMIT = 100_000
export const COLLAPSE_THRESHOLD = 3

export function detectStructured(message: string | undefined | null): boolean {
    if (!message) {
        return false
    }
    const trimmed = message.trim()
    if (trimmed.length < 2) {
        return false
    }
    const first = trimmed[0]
    const last = trimmed[trimmed.length - 1]
    return (first === "{" && last === "}") || (first === "[" && last === "]")
}

export function parseStructured(message: string | undefined | null): unknown | undefined {
    if (!detectStructured(message) || (message as string).length > STRUCTURED_PARSE_LIMIT) {
        return undefined
    }
    try {
        return JSON.parse((message as string).trim())
    } catch {
        return undefined
    }
}

const TEMPLATE_MASKS: [RegExp, string][] = [
    [/\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:?\d{2})?/g, "§TS§"],
    [/\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b/gi, "§UUID§"],
    [/\b(?:[0-9a-f]{1,4}:){2,7}[0-9a-f]{1,4}\b/gi, "§IPV6§"],
    [/\b\d{1,3}(?:\.\d{1,3}){3}\b/g, "§IPV4§"],
    [/\[[^\]]+\]/g, "[§]"],
    [/\b\d{3,}\b/g, "§N§"],
]

export function normalizeLogTemplate(message: string | undefined | null): string {
    let template = (message ?? "").trim()
    for (const [pattern, mask] of TEMPLATE_MASKS) {
        template = template.replace(pattern, mask)
    }
    return template
}

export interface DisplayGroup {
    type: "group";
    templateKey: string;
    logs: Log[];
}

export function pushLog(groups: DisplayGroup[], log: Log): DisplayGroup[] {
    const templateKey = normalizeLogTemplate(log.message)
    const last = groups[groups.length - 1]
    if (last && last.templateKey === templateKey) {
        last.logs.push(log)
    } else {
        groups.push({type: "group", templateKey, logs: [log]})
    }
    return groups
}

export function groupConsecutive(logs: Log[]): DisplayGroup[] {
    return logs.reduce<DisplayGroup[]>((groups, log) => pushLog(groups, log), [])
}

export function isCollapsible(group: DisplayGroup): boolean {
    return group.logs.length >= COLLAPSE_THRESHOLD
}


export function color() {
    return Object.fromEntries(LOG_LEVELS.map(level => [level, cssVar("--log-chart-" + level.toLowerCase())]))
}

export function graphColors(state: LevelKey) {
    const COLORS = {
        ERROR: "#AB0009",
        WARN: "#DD5F00",
        INFO: "#029E73",
        DEBUG: "#1761FD",
        TRACE: "#8405FF",
    }

    return COLORS[state]
}

export function chartColorFromLevel(level: LevelKey, alpha = 1) {
    const hex = color()[level]
    if (!hex) {
        return null
    }

    const [r, g, b] = hex.match(/\w\w/g)?.map(x => parseInt(x, 16)) || []
    return `rgba(${r},${g},${b},${alpha})`
}

export function sort(value: Record<string, any>) {
    return Object.keys(value)
        .sort((a, b) => {
            return index(LOG_LEVELS, a) - index(LOG_LEVELS, b)
        })
        .reduce(
            (obj, key) => {
                obj[key] = value[key]
                return obj
            },
            {} as Record<string, any>,
        )
}

export function index(based: readonly string[], value: string) {
    const idx = based.indexOf(value)

    return idx === -1 ? Number.MAX_SAFE_INTEGER : idx
}

export function levelOrLower(level: LevelKey) {
    const levels: LevelKey[] = []
    for (const currentLevel of LOG_LEVELS) {
        levels.push(currentLevel)
        if (currentLevel === level) {
            break
        }
    }
    return levels.reverse()
}

type DownloadableLog = Pick<Log, "timestamp" | "message"> & {level?: Log["level"]}

export function formatLogsAsText(logs: DownloadableLog[]): string {
    return logs
        .map((log) => `${(log.level ?? "").padEnd(5)} ${log.timestamp} ${(log.message ?? "").replace(/\s+$/, "")}`)
        .join("\n")
}

export function logsDownloadFilename(date: Date): string {
    const pad = (value: number) => String(value).padStart(2, "0")
    const stamp = `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}-${pad(date.getHours())}${pad(date.getMinutes())}${pad(date.getSeconds())}`
    return `logs-${stamp}.log`
}

export function executionLogsDownloadFilename(executionId: string, date: Date): string {
    const pad = (value: number) => String(value).padStart(2, "0")
    const stamp = `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}${pad(date.getHours())}${pad(date.getMinutes())}${pad(date.getSeconds())}`
    return `kestra-execution-${stamp}-${executionId}.log`
}

