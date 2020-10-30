const iso = date => new Date(parseInt(date)).toISOString()

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

    static build(route) {
        const q = route.query;
        const query = []

        if (q.namespace) {
            query.push(`namespace:${q.namespace}`)
        }

        if (q.start) {
            query.push(`state.startDate:[${iso(q.start)} TO *]`)
        }

        if (q.end) {
            query.push(`state.endDate:[* TO ${iso(q.end)}]`)
        }
        if (q.q) {
            query.push(QueryBuilder.toLucene(q.q));
        }

        return query.join(" AND ") || '*'
    }

    static logQueryBuilder(route) {
        const q = route.query
        const start = q.start ? iso(q.start) : '*'
        const end = q.end ? iso(q.end) : '*'
        return [
            `${q.q ? QueryBuilder.toLucene(q.q) : "*"}`,
            `timestamp:[${start} TO ${end}]`,
            `namespace:${q.namespace || "*"}`,
        ].join(" AND ")
    }
}
