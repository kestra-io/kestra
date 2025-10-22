import moment from "moment/moment";
import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";

export type InputType = "STRING" | "NUMBER" | "BOOLEAN" | "BOOL" | "DATE" | "DATETIME" | "TIME" | "ARRAY" | "MULTISELECT" | "JSON" | "YAML" | "SECRET" | "FILE";

export default class Inputs {
    static normalize(type: InputType | undefined, value: any) {
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

    static normalizeForComponents(type: InputType | undefined, value: any) {
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