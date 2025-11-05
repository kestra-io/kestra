import {cssVariable, LOG_LEVELS} from "@kestra-io/ui-libs";

export type LevelKey = typeof LOG_LEVELS[number];


export function color() {
    return Object.fromEntries(LOG_LEVELS.map(level => [level, cssVariable("--log-chart-" + level.toLowerCase())]));
}

export function graphColors(state: LevelKey) {
    const COLORS = {
        ERROR: "#AB0009",
        WARN: "#DD5F00",
        INFO: "#029E73",
        DEBUG: "#1761FD",
        TRACE: "#8405FF",
    };

    return COLORS[state];
}

export function chartColorFromLevel(level: LevelKey, alpha = 1) {
    const hex = color()[level];
    if (!hex) {
        return null;
    }

    const [r, g, b] = hex.match(/\w\w/g)?.map(x => parseInt(x, 16)) || [];
    return `rgba(${r},${g},${b},${alpha})`;
}

export function sort(value: Record<string, any>) {
    return Object.keys(value)
        .sort((a, b) => {
            return index(LOG_LEVELS, a) - index(LOG_LEVELS, b);
        })
        .reduce(
            (obj, key) => {
                obj[key] = value[key];
                return obj;
            },
            {} as Record<string, any>
        );
}

export function index(based: readonly string[], value: string) {
    const index = based.indexOf(value);

    return index === -1 ? Number.MAX_SAFE_INTEGER : index;
}

export function levelOrLower(level: LevelKey) {
    const levels: LevelKey[] = [];
    for (const currentLevel of LOG_LEVELS) {
        levels.push(currentLevel);
        if (currentLevel === level) {
            break;
        }
    }
    return levels.reverse();
}

