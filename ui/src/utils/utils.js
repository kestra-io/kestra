import moment from "moment";
import humanizeDuration from "humanize-duration";

const humanizeDurationLanguages = {
    "en" : {
        y: () => "y",
        mo: () => "mo",
        w: () => "w",
        d: () => "d",
        h: () => "h",
        m: () => "m",
        s: () => "s",
        ms: () => "ms",
    },
    "fr" : {
        y: () => "a",
        mo: () => "mo",
        w: () => "se",
        d: () => "j",
        h: () => "h",
        m: () => "m",
        s: () => "s",
        ms: () => "ms",
    },
    "zh_CN" : {
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

    static flatten(object) {
        return Object.assign({}, ...function _flatten(child, path = []) {
            if (child === null) {
                return {[path.join(".")]: null};
            }

            return []
                .concat(...Object
                    .keys(child === null ? [] : child)
                    .map(key => typeof child[key] === "object" ?
                        _flatten(child[key], path.concat([key])) :
                        ({[path.concat([key]).join(".")]: child[key]})
                    )
                );
        }(object));
    }

    static executionVars(data) {
        if (data === undefined) {
            return [];
        }

        const flat = Utils.flatten(data);

        return Object.keys(flat).map(key => {
            let rawValue = flat[key];
            if (key === "variables.executionId") {
                return {key, value: rawValue, subflow: true};
            }

            if (typeof rawValue === "string" && rawValue.match(/\d{4}-\d{2}-\d{2}/)) {
                let date = moment(rawValue, moment.ISO_8601);
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
    static humanFileSize(bytes, si = false, dp = 1) {
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

    static duration(isoString) {
        return moment.duration(isoString, moment.ISO_8601).asMilliseconds() / 1000
    }

    static humanDuration(value, options) {
        options = options || {maxDecimalPoints: 2};
        options.spacer = "";
        options.language = Utils.getLang();
        options.languages = humanizeDurationLanguages;
        options.largest = 2;

        if (typeof (value) !== "number") {
            value = Utils.duration(value);
        }

        return humanizeDuration(value * 1000, options).replace(/\.([0-9])s$/i, ".$10s")
    }

    static number(number) {
        return number.toString().replace(/(\d)(?=(\d{3})+(?!\d))/g, "$1 ");
    }

    static hexToRgba(hex, opacity) {
        let c;
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

    static downloadUrl(url, filename) {
        const link = document.createElement("a");
        link.href = url;
        link.setAttribute("download", filename);
        link.setAttribute("target", "_blank");
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
    }

    /**
     * Extracts a filename from an HTTP 'Content-Disposition` header.
     *
     * @param header  the header value
     * @returns {*|string|null}
     */
    static extratFileNameFromContentDisposition(header) {
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

    static switchTheme(theme) {
        // default theme
        if (theme === undefined) {
            if (localStorage.getItem("theme")) {
                theme =  localStorage.getItem("theme");
            } else if (window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches) {
                theme = "dark";
            } else {
                theme = "light";
            }
        }

        // class name
        let htmlClass = document.getElementsByTagName("html")[0].classList;

        function removeClasses()
        {
            htmlClass.forEach((cls) => {
            if (cls === "dark" || cls === "light" || cls === "syncWithSystem") {
                htmlClass.remove(cls);
            }
            })
        }
        removeClasses();

        if(theme === "syncWithSystem") {
            removeClasses();
            const systemTheme = window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
            htmlClass.add(theme, systemTheme);
        }
        else {
            removeClasses();
            htmlClass.add(theme);
        }
        localStorage.setItem("theme", theme);
    }

    static getTheme(which = "theme") {
        let theme = localStorage.getItem(which) || "light";

        if(theme === "syncWithSystem") {
            theme = window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
        }

        return theme;
    }

    static getLang() {
        return localStorage.getItem("lang") || "en";
    }

    static splitFirst(str, separator){
        return str.split(separator).slice(1).join(separator);
    }

    static asArray(objOrArray) {
        if(objOrArray === undefined) {
            return [];
        }

        return Array.isArray(objOrArray) ? objOrArray : [objOrArray];
    }

    static async copy(text) {
        if(navigator.clipboard) {
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

    static toFormData(obj) {
        if (!(obj instanceof FormData)) {
            const formData = new FormData();
            for (const key in obj) {
                formData.append(key, obj[key]);
            }
            return formData;
        }
        return obj;
    }

    static getDateFormat(startDate, endDate) {
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
}
