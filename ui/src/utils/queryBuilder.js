import moment from "moment";

export default class QueryBuilder {
    static toLucene(q) {
        return "(*" + q.split(/[\W_]+/g).join("* AND *") + "*)";
    }

    static iso(date) {
        return moment(new Date(parseInt(date))).toISOString(true);
    }
}
