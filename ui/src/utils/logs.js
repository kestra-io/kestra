import _mapValues from "lodash/mapValues";
import {cssVariable} from "@kestra-io/ui-libs/src/utils/global";

const LEVELS = Object.freeze({
    ERROR: {
        name: "ERROR",
        colorClass: "red",
    },
    WARN: {
        name: "WARN",
        colorClass: "orange",
    },
    INFO: {
        name: "INFO",
        colorClass: "cyan",
    },
    DEBUG: {
        name: "DEBUG",
        colorClass: "purple",
    },
    TRACE: {
        name: "TRACE",
        colorClass: "gray",
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
        return _mapValues(LEVELS, level => cssVariable("--bs-" + level.colorClass));
    }

    static backgroundFromLevel(level, alpha = 1) {
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
}
