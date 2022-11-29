import Utils from "./utils";
import {getCurrentInstance} from "vue";

export default {
    humanizeDuration: (value, options) => {
        return Utils.humanDuration(value, options);
    },
    humanizeNumber: (value) => {
        return parseInt(value).toLocaleString(localStorage.getItem("lang") || "en")
    },
    cap: value => value ? value.toString().capitalize() : "",
    lower: value => value ? value.toString().toLowerCase() : "",
    date: (dateString, format) => {
        let f;
        if (format === undefined) {
            f = "LLLL"
        } else if (format === "iso") {
            f = "YYYY-MM-DD HH:mm:ss.SSS"
        } else {
            f = format
        }
        return getCurrentInstance().appContext.config.globalProperties.$moment(dateString).format(f)
    }
}


