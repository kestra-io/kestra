import Vue from "vue"
import Utils from "./utils/utils";

Vue.filter("id", value => value ? value.toString().substr(0, 8) : "");

Vue.filter("humanizeDuration", (value, options) => {
    return Utils.humanDuration(value, options);
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
Vue.filter("ellipsis", (text, len) => text.length > len ? text.substr(0, len) + "..." : text.substr(0, len))
