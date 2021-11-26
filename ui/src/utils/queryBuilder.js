import moment from "moment";

export default class QueryBuilder {
    static toLucene(q) {
        const split = q.split(/[^a-zA-Z0-9_.-]+/g);

        let query = "(*" + split.join("*") + "*)^3 OR (*" + split.join("* AND *") + "*)";

        if (split.length === 1 ) {
            query = `(${q})^5 OR ${query}`
        }

        return `(${query})`;
    }

    static iso(date) {
        return moment(new Date(parseInt(date))).toISOString(true);
    }
}
