export default (route, fields) => {
    const q = route.query.q;
    const query = []
    if (route.query.namespace) {
        query.push(`namespace:${route.query.namespace}`)
    }
    if (route.query.start) {
        query.push(`state.startDate:[${new Date(parseInt(route.query.start)).toISOString()} TO *]`)
    }
    if (route.query.end) {
        query.push(`state.endDate:[* TO ${new Date(parseInt(route.query.end)).toISOString()}]`)
    }
    if (q) {
        const search = fields
            .map(f => `${f.key}:*${q}* OR ${f.key}:${q}`)
            .join(" OR ")
        query.push('(' + search + ')');
    }
    return query.join(' AND ') || '*'
}