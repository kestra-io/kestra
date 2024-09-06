import _mapValues from "lodash/mapValues";
import {cssVariable} from "@kestra-io/ui-libs/src/utils/global";

const LEVELS = Object.freeze({
    ERROR: {
        name: "ERROR",
        fullName: "ERROR",
    },
    WARN: {
        name: "WARN",
        fullName: "WARNING",
    },
    INFO: {
        name: "INFO",
        fullName: "INFO",
    },
    DEBUG: {
        name: "DEBUG",
        fullName: "DEBUG",
    },
    TRACE: {
        name: "TRACE",
        fullName: "TRACE",
    },
});

export default class Logs {
    static get ERROR() {
        return LEVELS.ERROR.name;
    }

    static get WARN() {
        return LEVELS.WARN.name;
    }

    static get INFO() {
        return LEVELS.INFO.name;
    }

    static get DEBUG() {
        return LEVELS.DEBUG.name;
    }

    static get TRACE() {
        return LEVELS.TRACE.name;
    }

    static color() {
        return _mapValues(LEVELS, level => cssVariable("--log-chart-" + level.name.toLowerCase()));
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
        const SORT_FIELDS = Object.keys(LEVELS)

        return Object.keys(value)
            .sort((a, b) => {
                return Logs.index(SORT_FIELDS, a) - Logs.index(SORT_FIELDS, b);
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

    static fromName(name) {
        return LEVELS?.[name];
    }

    static levelOrLower(level) {
        const levels = [];
        for (const [key, value] of Object.entries(LEVELS)) {
            levels.push(value);
            if (key === level) {
                break;
            }
        }
        return levels.reverse();
    }
}
