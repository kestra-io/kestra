import Vue from "vue"
import Utils from "./utils/utils";

Vue.filter("humanizeDuration", (value, options) => {
    return Utils.humanDuration(value, options);
});

Vue.filter("humanizeNumber", (value) => {
    return parseInt(value).toLocaleString(localStorage.getItem("lang") || "en")
});

Vue.filter("cap", value => value ? value.toString().capitalize() : "");

Vue.filter("lower", value => value ? value.toString().toLowerCase() : "");

Vue.filter("date", (dateString, format) => {
    let f;
    if (format === undefined) {
        f = "LLLL"
    } else if (format === "iso") {
        f = "YYYY-MM-DD HH:mm:ss.SSS"
    } else {
        f = format
    }
    return Vue.moment(dateString).format(f)
})
