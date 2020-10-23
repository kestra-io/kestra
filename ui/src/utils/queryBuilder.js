const iso = date => new Date(parseInt(date)).toISOString()
export default {
    executionQueryBuilder: (route, fields) => {
        const q = route.query;

        const query = [
            `namespace:${q.namespace || '*'}`,
        ]
        if (q.start) {
            query.push(`state.startDate:[${iso(q.start)} TO *]`)
        }
        if (q.end) {
            query.push(`state.endDate:[* TO ${iso(q.end)}]`)
        }
        if (q.q) {
            // Generate search string on all fields
            const search = fields
                .map(f => `${f.key}:*${q.q}* OR ${f.key}:${q.q}`)
                .join(" OR ")
            query.push('(' + search + ')');
        }

        return query.join(" AND ") || '*'
    },
    logQueryBuilder: (route) => {
        const q = route.query
        const start = q.start ? iso(q.start) : '*'
        const end = q.end ? iso(q.end) : '*'
        return [
            `message:${q.q ? '*' + q.q + '*' : "*"}`,
            `timestamp:[${start} TO ${end}]`,
            `namespace:${q.namespace || "*"}`,
        ].join(" AND ")
    }
}
