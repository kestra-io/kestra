import Utils from "./utils";
import {getCurrentInstance} from "vue";
import {DATE_FORMAT_STORAGE_KEY} from "../components/settings/Settings.vue";

export default {
    invisibleSpace: (value) => {
        return value.replaceAll(".", "\u200B" + ".");
    },
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
        if (format === "iso") {
            f = "YYYY-MM-DD HH:mm:ss.SSS"
        } else {
            f = format ?? localStorage.getItem(DATE_FORMAT_STORAGE_KEY) ?? "LLLL";
        }
        return getCurrentInstance().appContext.config.globalProperties.$moment(dateString).format(f)
    }
}


