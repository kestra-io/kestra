export interface StateGroup {
    key: string;
    labelKey: string;
    token: string;
    states: string[];
}

export const STATE_GROUPS: StateGroup[] = [
    {
        key: "running",
        labelKey: "filter.state_group.running",
        token: "--ks-status-running",
        states: ["SUBMITTED", "CREATED", "RESTARTED", "QUEUED", "RUNNING", "RETRYING"],
    },
    {
        key: "paused",
        labelKey: "filter.state_group.paused",
        token: "--ks-status-paused",
        states: ["PAUSED", "BREAKPOINT"],
    },
    {
        key: "completed",
        labelKey: "filter.state_group.completed",
        token: "--ks-status-success",
        states: ["SUCCESS", "WARNING", "SKIPPED", "RETRIED"],
    },
    {
        key: "failed",
        labelKey: "filter.state_group.failed",
        token: "--ks-status-error",
        states: ["FAILED", "KILLING", "KILLED", "CANCELLED"],
    },
]

const KNOWN_STATES = new Set(STATE_GROUPS.flatMap(g => g.states))

export interface ResolvedGroup {
    key: string;
    labelKey: string;
    token: string;
    options: {value: string; label: string}[];
}

export function resolveStateGroups(options: {value: string; label: string}[]): ResolvedGroup[] {
    const optionMap = new Map(options.map(o => [o.value, o]))
    const assigned = new Set<string>()

    const groups: ResolvedGroup[] = STATE_GROUPS.flatMap(g => {
        const matched = g.states.filter(s => {
            if (optionMap.has(s)) {
                assigned.add(s)
                return true
            }
            return false
        }).map(s => optionMap.get(s)!)

        if (matched.length === 0) return []
        return [{key: g.key, labelKey: g.labelKey, token: g.token, options: matched}]
    })

    const others = options.filter(o => !KNOWN_STATES.has(o.value))
    if (others.length > 0) {
        groups.push({key: "other", labelKey: "filter.state_group.other", token: "--ks-status-neutral", options: others})
    }

    return groups
}
