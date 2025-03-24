import {cssVariable} from "@kestra-io/ui-libs";

const LEVELS = [
    "ERROR",
    "WARN",
    "INFO",
    "DEBUG",
    "TRACE"
];

export default class Logs {
    static color() {
        return Object.fromEntries(LEVELS.map(level => [level, cssVariable("--log-chart-" + level.toLowerCase())]));
    }

    static graphColors(state) {
        const COLORS = {
            ERROR: "#AB0009",
            WARN: "#DD5F00",
            INFO: "#029E73",
            DEBUG: "#1761FD",
            TRACE: "#8405FF",
        };

        return COLORS[state];
    }

    static chartColorFromLevel(level, alpha = 1) {
        const hex = Logs.color()[level];
        if (!hex) {
            return null;
        }

        const [r, g, b] = hex.match(/\w\w/g).map(x => parseInt(x, 16));
        return `rgba(${r},${g},${b},${alpha})`;
    }

    static sort(value) {
        return Object.keys(value)
            .sort((a, b) => {
                return Logs.index(LEVELS, a) - Logs.index(LEVELS, b);
            })
            .reduce(
                (obj, key) => {
                    obj[key] = value[key];
                    return obj;
                },
                {}
            );
    }

    static index(based, value) {
        const index = based.indexOf(value);

        return index === -1 ? Number.MAX_SAFE_INTEGER : index;
    }

    static levelOrLower(level) {
        const levels = [];
        for (const currentLevel of LEVELS) {
            levels.push(currentLevel);
            if (currentLevel === level) {
                break;
            }
        }
        return levels.reverse();
    }
}
