import moment from "moment/moment";
import YamlUtils from "./yamlUtils.js";

export default class Inputs {
    static normalize(type, value) {
        let res = value;

        if (value === null || value === undefined) {
            res = undefined;
        } else if (type === "DATE" || type === "DATETIME") {
            res = moment(res).toISOString()
        } else if (type === "DURATION" || type === "TIME") {
            res = moment().startOf("day").add(res, "seconds").toString()
        } else if (type === "ARRAY" || type === "MULTISELECT" || type === "JSON") {
            if(typeof res !== "string") {
                res = JSON.stringify(res).toString();
            }
        } else if (type === "YAML") {
            if(typeof res !== "string") {
                res = YamlUtils.stringify(res).toString();
            }
        } else if (type === "BOOLEAN" && type === undefined){
            res = "undefined";
        } else if (type === "STRING" && Array.isArray(res)){
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
        } else if (type === "DURATION" || type === "TIME") {
            res = moment().startOf("day").add(res, "seconds").toString()
        } else if (type === "ARRAY") {
            res = JSON.stringify(res).toString();
        } else if (type === "BOOLEAN" && type === undefined){
            res = "undefined";
        } else if (type === "STRING" && Array.isArray(res)){
            res = res.toString();
        }
        return res;
    }
}