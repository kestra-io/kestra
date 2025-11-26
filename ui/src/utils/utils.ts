import {computed} from "vue";
import moment from "moment";
import humanizeDuration from "humanize-duration";
import {useMiscStore} from "override/stores/misc";

const humanizeDurationLanguages = {
    "en": {
        y: () => "y",
        mo: () => "mo",
        w: () => "w",
        d: () => "d",
        h: () => "h",
        m: () => "m",
        s: () => "s",
        ms: () => "ms",
    },
    "fr": {
        y: () => "a",
        mo: () => "mo",
        w: () => "se",
        d: () => "j",
        h: () => "h",
        m: () => "m",
        s: () => "s",
        ms: () => "ms",
    },
    "zh_CN": {
        y: () => "年",
        mo: () => "月",
        w: () => "周",
        d: () => "天",
        h: () => "小时",
        m: () => "分钟",
        s: () => "秒",
        ms: () => "毫秒",
    }
}

export default class Utils {
    static uid() {
        return String.fromCharCode(Math.floor(Math.random() * 26) + 97) +
            Math.random().toString(16).slice(2) +
            Date.now().toString(16).slice(4);
    }

    static flatten(object: Record<string, any>) {
        return Object.assign({}, function _flatten(child: Record<string, any> | null, path: string[] = []): Record<string, any> {
            if (child === null) {
                return {[path.join(".")]: null};
            }

            return Object
                .keys(child)
                .map(key => typeof child[key] === "object" ?
                    _flatten(child[key], path.concat([key])) :
                    ({[path.concat([key]).join(".")]: child[key]})
                );
        }(object));
    }

    static executionVars(data: Record<string, any>) {
        if (data === undefined) {
            return [];
        }

        const flat = Utils.flatten(data);

        return Object.keys(flat).map(key => {
            const rawValue = flat[key];
            if (key === "variables.executionId") {
                return {key, value: rawValue, subflow: true};
            }

            if (typeof rawValue === "string" && rawValue.match(/\d{4}-\d{2}-\d{2}/)) {
                const date = moment(rawValue, moment.ISO_8601);
                if (date.isValid()) {
                    return {key, value: rawValue, date: true};
                }
            }

            if (typeof rawValue === "number") {
                return {key, value: Utils.number(rawValue)};
            }

            return {key, value: rawValue};

        })
    }

    /**
     * Format bytes as human-readable text.
     *
     * @param bytes Number of bytes.
     * @param si True to use metric (SI) units, aka powers of 1000. False to use
     *           binary (IEC), aka powers of 1024.
     * @param dp Number of decimal places to display.
     *
     * @return Formatted string.
     */
    static humanFileSize(bytes: number, si = false, dp = 1) {
        if (bytes === undefined) {
            // when the size is 0 it arrives as undefined here!
            return "0B";
        }
        const thresh = si ? 1000 : 1024;

        if (Math.abs(bytes) < thresh) {
            return bytes + " B";
        }

        const units = si ?
            ["kB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB"] :
            ["KiB", "MiB", "GiB", "TiB", "PiB", "EiB", "ZiB", "YiB"];
        let u = -1;
        const r = 10 ** dp;

        do {
            bytes /= thresh;
            ++u;
        } while (Math.round(Math.abs(bytes) * r) / r >= thresh && u < units.length - 1);


        return bytes.toFixed(dp) + " " + units[u];
    }

    static duration(isoString: string) {
        return moment.duration(isoString).asMilliseconds() / 1000
    }

    static humanDuration(value: string | number, options?: humanizeDuration.HumanizerOptions) {
        options = options || {maxDecimalPoints: 2};
        options.spacer = "";
        options.language = Object.keys(humanizeDurationLanguages).includes(Utils.getLang()) ? Utils.getLang() : "en";
        options.languages = humanizeDurationLanguages;
        options.largest = 2;

        if (typeof (value) !== "number") {
            value = Utils.duration(value);
        }

        const humanizer = humanizeDuration.humanizer(options);
        return humanizer(value * 1000).replace(/\.([0-9])s$/i, ".$10s")
    }

    static number(number: number) {
        return number.toString().replace(/(\d)(?=(\d{3})+(?!\d))/g, "$1 ");
    }

    static hexToRgba(hex: string, opacity: number) {
        let c: any;
        if (/^#([A-Fa-f0-9]{3}){1,2}$/.test(hex)) {
            c = hex.substring(1).split("");
            if (c.length === 3) {
                c = [c[0], c[0], c[1], c[1], c[2], c[2]];
            }
            c = "0x" + c.join("");
            return "rgba(" + [(c >> 16) & 255, (c >> 8) & 255, c & 255].join(",") + "," + (opacity || 1) + ")";
        }
        throw new Error("Bad Hex");
    }

    static downloadUrl(url: string, filename: string) {
        const link = document.createElement("a");
        link.href = url;
        link.setAttribute("download", filename);
        link.setAttribute("target", "_blank");
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
    }

    /**
     * Extracts a filename from an HTTP `Content-Disposition` header.
     *
     * @param header  the header value
     */
    static extractFileNameFromContentDisposition(header: string | null | undefined): string | null {
        if (!header) return null;

        const filenameRegex = /filename\*=UTF-8''(.+)|filename="(.+?)"|filename=(.+)/;
        const matches = header.match(filenameRegex);

        // Check for UTF-8 encoded filename first
        if (matches && matches[1]) {
            return decodeURIComponent(matches[1]);
        }
        // Fallback to quoted or unquoted filename
        if (matches && matches[2]) {
            return matches[2];
        }
        if (matches && matches[3]) {
            return matches[3];
        }

        return null; // Return null if no filename is found
    }

    static switchTheme(miscStore: any, theme?: string) {
        // default theme
        if (theme === undefined) {
            if (localStorage.getItem("theme")) {
                theme = localStorage.getItem("theme")!;
            } else if (window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches) {
                theme = "dark";
            } else {
                theme = "light";
            }
        }

        // class name
        const htmlClass = document.getElementsByTagName("html")[0].classList;

        function removeClasses() {
            htmlClass.forEach((cls) => {
                if (cls === "dark" || cls === "light" || cls === "syncWithSystem") {
                    htmlClass.remove(cls);
                }
            })
        }
        removeClasses();

        if (theme === "syncWithSystem") {
            removeClasses();
            const systemTheme = window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
            htmlClass.add(theme, systemTheme);
        }
        else {
            removeClasses();
            htmlClass.add(theme);
        }

        miscStore.theme = theme;

        localStorage.setItem("theme", theme);
    }

    static getTheme(): "light" | "dark" {
        let theme = (localStorage.getItem("theme") as "syncWithSystem" | "dark" | "light" | null) ?? "light";

        if (theme === "syncWithSystem") {
            theme = window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
        }

        return theme;
    }

    static getLang() {
        return localStorage.getItem("lang") || "en";
    }

    static splitFirst(str: string, separator: string) {
        return str.split(separator).slice(1).join(separator);
    }

    static asArray(objOrArray: any | any[]) {
        if (objOrArray === undefined) {
            return [];
        }

        return Array.isArray(objOrArray) ? objOrArray : [objOrArray];
    }

    static async copy(text: string) {
        if (navigator.clipboard) {
            await navigator.clipboard.writeText(text);
            return;
        }

        const node = document.createElement("textarea");
        node.style.position = "absolute";
        node.style.left = "-9999px";
        node.textContent = text;
        document.body.appendChild(node).value = text;
        node.select()

        document.execCommand("copy");

        document.body.removeChild(node);
    }

    static toFormData(obj: FormData | Record<string, any>) {
        if (!(obj instanceof FormData)) {
            const formData = new FormData();
            for (const key in obj) {
                formData.append(key, obj[key]);
            }
            return formData;
        }
        return obj;
    }

    static getDateFormat(startDate: moment.MomentInput, endDate: moment.MomentInput) {
        if (!startDate || !endDate) {
            return "yyyy-MM-DD";
        }

        const duration = moment.duration(moment(endDate).diff(moment(startDate)));

        if (duration.asDays() > 365) {
            return "yyyy-MM";
        } else if (duration.asDays() > 180) {
            return "yyyy-'W'ww";
        } else if (duration.asDays() > 1) {
            return "yyyy-MM-DD";
        } else if (duration.asHours() > 1) {
            return "yyyy-MM-DD:HH:00";
        } else {
            return "yyyy-MM-DD:HH:mm";
        }
    }

    static getParentNamespaces(namespace: string): string[] {
        if (!namespace) return [];

        const parts = namespace.split(".");
        const parents: string[] = [];

        for (let i = 1; i <= parts.length; i++) {
            parents.push(parts.slice(0, i).join("."));
        }

        return parents;
    }
}

export const useTheme = () => {
    const miscStore = useMiscStore();
    return computed<"light" | "dark">(() => miscStore.theme as "light" | "dark");
}

export function resolve$ref(fullSchema: Record<string, any>, obj: Record<string, any>,) {
    if (obj === undefined || obj === null) {
        return obj;
    }
    if (obj.$ref) {
        return getValueAtJsonPath(fullSchema, obj.$ref);
    }
    return obj;
}

export function getValueAtJsonPath(fullSchema: Record<string, any>, path: string): any {
    if (!fullSchema || !path || typeof path !== "string") {
        return undefined;
    }

    const keys = path.replace(/^#\//, "").split("/");
    let current = fullSchema;

    for (const key of keys) {
        if (current && key in current) {
            current = resolve$ref(fullSchema, current[key]);
        } else {
            return undefined;
        }
    }

    return current;
}

export function deepEqual(x: any, y: any): boolean {
    const ok = Object.keys, tx = typeof x, ty = typeof y;
    return x && y && tx === "object" && tx === ty ? (
        ok(x).length === ok(y).length &&
        ok(x).every(key => deepEqual(x[key], y[key]))
    ) : (x === y);
}
