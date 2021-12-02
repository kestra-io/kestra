import moment from "moment";

export default class QueryBuilder {
    static split(q) {
        return q.split(/[^a-zA-Z0-9_.-]+/g)
            .filter(r => r !== "");
    }

    static toLucene(q) {
        const split = QueryBuilder.split(q);

        let query = "(*" + split.join("*") + "*)^3 OR (*" + split.join("* AND *") + "*)";

        if (split.length === 1 ) {
            query = `(${q})^5 OR ${query}`
        }

        return `(${query})`;
    }

    static toTextLucene(q) {
        const split = QueryBuilder.split(q);

        return `(${split.join(" AND ") })`;
    }

    static iso(date) {
        return moment(new Date(parseInt(date))).toISOString(true);
    }
}
