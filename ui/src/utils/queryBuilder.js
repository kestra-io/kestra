import moment from "moment";

export default class QueryBuilder {
    static toLucene(q) {
        let query = q;

        query = query.replace(/</g, "");
        query = query.replace(/>/g, "");
        query = query.replace(/\//g, "");
        query = query.replace(/\\/g, "");
        query = query.replace(/&/g, "");
        query = query.replace(/!/g, "");
        query = query.replace(/([\^~*?:"+-=|(){}[\]])/g, "\\$1")

        return `(*${query}* OR ${query})`;
    }

    static iso(date) {
        return moment(new Date(parseInt(date))).toISOString(true);
    }
}
