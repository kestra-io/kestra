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
            return []
                .concat(...Object
                    .keys(child)
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
            if (key === "variables.executionId") {
                return {key, value: flat[key], subflow: true};
            }

            if (typeof (flat[key]) === "string") {
                let date = moment(flat[key], moment.ISO_8601);
                if (date.isValid()) {
                    return {key, value: flat[key], date: true};
                }
            }

            if (typeof (flat[key]) === "number") {
                return {key, value: Utils.number(flat[key])};
            }

            return {key, value: flat[key]};

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
        options.language = localStorage.getItem("lang") || "en";
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
        document.body.appendChild(link);
        link.click();
    }
}
