import moment from "moment/moment";
import {YamlUtils as YAML_UTILS} from "@kestra-io/ui-libs";

export default class Inputs {
    static normalize(type, value) {
        let res = value;

        if (type === "BOOLEAN" && value === undefined) {
            res = "undefined";
        } else if (type === "BOOL" && value === undefined) {
            res = false
        } else if (value === null || value === undefined) {
            res = undefined;
        } else if (type === "DATE" || type === "DATETIME") {
            res = moment(res).toISOString()
        } else if (type === "TIME") {
            res = moment().startOf("day").add(res, "seconds").toString()
        } else if (type === "ARRAY" || type === "MULTISELECT" || type === "JSON") {
            if (typeof res !== "string") {
                res = JSON.stringify(res).toString();
            }
        } else if (type === "YAML") {
            if (typeof res !== "string") {
                res = YAML_UTILS.stringify(res).toString();
            }
        } else if (type === "STRING" && Array.isArray(res)) {
            res = res.toString();
        }
        return res;
    }

    static normalizeForComponents(type, value) {
        let res = value;

        if (value === null) {
            res = undefined;
        } else if (type === "DATE" || type === "DATETIME") {
            res = moment(res).toISOString()
        } else if (type === "TIME") {
            res = moment().startOf("day").add(res, "seconds").toString()
        } else if (type === "ARRAY") {
            res = JSON.stringify(res).toString();
        } else if (type === "BOOLEAN" && type === undefined) {
            res = "undefined";
        } else if (type === "BOOL" && value === undefined) {
            res = false
        } else if (type === "STRING" && Array.isArray(res)) {
            res = res.toString();
        }
        return res;
    }
}