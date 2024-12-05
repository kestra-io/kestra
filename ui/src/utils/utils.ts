import moment from "moment";
import humanizeDuration from "humanize-duration";

const humanizeDurationLanguages = {
    en: {
        y: () => "y",
        mo: () => "mo",
        w: () => "w",
        d: () => "d",
        h: () => "h",
        m: () => "m",
        s: () => "s",
        ms: () => "ms",
    },
    fr: {
        y: () => "a",
        mo: () => "mo",
        w: () => "se",
        d: () => "j",
        h: () => "h",
        m: () => "m",
        s: () => "s",
        ms: () => "ms",
    },
    zh_CN: {
        y: () => "年",
        mo: () => "月",
        w: () => "周",
        d: () => "天",
        h: () => "小时",
        m: () => "分钟",
        s: () => "秒",
        ms: () => "毫秒",
    },
};

export default {
    uid() {
        return (
            String.fromCharCode(Math.floor(Math.random() * 26) + 97) +
            Math.random().toString(16).slice(2) +
            Date.now().toString(16).slice(4)
        );
    },

    flatten(object: Record<string, any>): Record<string, any> {
        return Object.assign(
            {},
            ...(function _flatten(child, path: string[] = []):any {
                if (child === null) {
                    return {[path.join(".")]: null};
                }

                return [].concat(
                    ...Object.keys(child === null ? [] : child).map((key: string) =>
                        typeof child[key] === "object"
                            ? _flatten(child[key], path.concat([key]))
                            : {[path.concat([key]).join(".")]: child[key]},
                    ),
                );
            })(object),
        );
    },

    executionVars(data: Record<string, any>) {
        if (data === undefined) {
            return [];
        }

        const flat = this.flatten(data);

        return Object.keys(flat).map((key) => {
            let rawValue = flat[key];
            if (key === "variables.executionId") {
                return {key, value: rawValue, subflow: true};
            }

            if (
                typeof rawValue === "string" &&
                rawValue.match(/\d{4}-\d{2}-\d{2}/)
            ) {
                let date = moment(rawValue, moment.ISO_8601);
                if (date.isValid()) {
                    return {key, value: rawValue, date: true};
                }
            }

            if (typeof rawValue === "number") {
                return {key, value: this.number(rawValue)};
            }

            return {key, value: rawValue};
        });
    },

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
    humanFileSize(bytes: number, si = false, dp = 1) {
        if (bytes === undefined) {
            // when the size is 0 it arrives as undefined here!
            return "0B";
        }
        const thresh = si ? 1000 : 1024;

        if (Math.abs(bytes) < thresh) {
            return bytes + " B";
        }

        const units = si
            ? ["kB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB"]
            : ["KiB", "MiB", "GiB", "TiB", "PiB", "EiB", "ZiB", "YiB"];
        let u = -1;
        const r = 10 ** dp;

        do {
            bytes /= thresh;
            ++u;
        } while (
            Math.round(Math.abs(bytes) * r) / r >= thresh &&
            u < units.length - 1
        );

        return bytes.toFixed(dp) + " " + units[u];
    },

    duration(isoString: string) {
        return (
            moment.duration(isoString, moment.ISO_8601 as any).asMilliseconds() / 1000
        );
    },

    humanDuration(value:number, options?:{
            maxDecimalPoints?:number,
        }) {
        const _options: {
            maxDecimalPoints?: number;
            spacer?: string;
            language?: string;
            languages?: Record<string, any>;
            largest?: number;
        } = options || {maxDecimalPoints: 2};
        _options.spacer = "";
        _options.language = this.getLang();
        _options.languages = humanizeDurationLanguages;
        _options.largest = 2;

        if (typeof value !== "number") {
            value = this.duration(value);
        }

        return humanizeDuration(value * 1000, options).replace(
            /\.([0-9])s$/i,
            ".$10s",
        );
    },

    number(number:number) {
        return number.toString().replace(/(\d)(?=(\d{3})+(?!\d))/g, "$1 ");
    },

    hexToRgba(hex: string, opacity: number) {
        let c: string[];
        if (/^#([A-Fa-f0-9]{3}){1,2}$/.test(hex)) {
            c = hex.substring(1).split("");
            if (c.length === 3) {
                c = [c[0], c[0], c[1], c[1], c[2], c[2]];
            }
            const cstr:any = "0x" + c.join("");
            return (
                "rgba(" +
                [(cstr >> 16) & 255, (cstr >> 8) & 255, cstr & 255].join(",") +
                "," +
                (opacity || 1) +
                ")"
            );
        }
        throw new Error("Bad Hex");
    },

    downloadUrl(url: string, filename: string) {
        const link = document.createElement("a");
        link.href = url;
        link.setAttribute("download", filename);
        link.setAttribute("target", "_blank");
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
    },

    switchTheme(theme: string) {
        // default theme
        if (theme === undefined) {
            const storedTheme = localStorage.getItem("theme");
            if (storedTheme) {
                theme = storedTheme;
            } else if (
                window.matchMedia &&
                window.matchMedia("(prefers-color-scheme: dark)").matches
            ) {
                theme = "dark";
            } else {
                theme = "light";
            }
        }

        // class name
        let htmlClass = document.getElementsByTagName("html")[0].classList;

        function removeClasses() {
            htmlClass.forEach((cls) => {
                if (
                    cls === "dark" ||
                    cls === "light" ||
                    cls === "syncWithSystem"
                ) {
                    htmlClass.remove(cls);
                }
            });
        }
        removeClasses();

        if (theme === "syncWithSystem") {
            removeClasses();
            const systemTheme =
                window.matchMedia &&
                window.matchMedia("(prefers-color-scheme: dark)").matches
                    ? "dark"
                    : "light";
            htmlClass.add(theme, systemTheme);
        } else {
            removeClasses();
            htmlClass.add(theme);
        }
        localStorage.setItem("theme", theme);
    },

    getTheme() {
        let theme = localStorage.getItem("theme") || "light";

        if (theme === "syncWithSystem") {
            theme =
                window.matchMedia &&
                window.matchMedia("(prefers-color-scheme: dark)").matches
                    ? "dark"
                    : "light";
        }

        return theme;
    },

    getLang() {
        return localStorage.getItem("lang") || "en";
    },

    splitFirst(str:string, separator:string) {
        return str.split(separator).slice(1).join(separator);
    },

    asArray(objOrArray?: any | any[]) {
        if (objOrArray === undefined) {
            return [];
        }

        return Array.isArray(objOrArray) ? objOrArray : [objOrArray];
    },

    async copy(text:string) {
        if (navigator.clipboard) {
            await navigator.clipboard.writeText(text);
            return;
        }

        const node = document.createElement("textarea");
        node.style.position = "absolute";
        node.style.left = "-9999px";
        node.textContent = text;
        document.body.appendChild(node).value = text;
        node.select();

        document.execCommand("copy");

        document.body.removeChild(node);
    },

    distinctFilter(value:string, index:number, array: string[]) {
        return array.indexOf(value) === index;
    },
};
