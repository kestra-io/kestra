import moment from "moment/moment";

export default class Inputs {
    static normalize(type, value) {
        let res = value;

        if (value === null) {
            res = undefined;
        } else if (type === "DATE" || type === "DATETIME") {
            res = moment(res).toISOString()
        } else if (type === "DURATION" || type === "TIME") {
            res = moment().startOf("day").add(res, "seconds").toString()
        } else if (type === "JSON" || type === "ARRAY") {
            res = JSON.stringify(res).toString()
        } else if (type === "BOOLEAN" && type === undefined){
            res = "undefined";
        }
        return res;
    }
}