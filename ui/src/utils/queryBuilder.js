export default (route, fields) => {
    const q = route.query.q;
    const query = []
    if (route.query.namespace) {
        query.push(`namespace:${route.query.namespace}`)
    }
    if (q) {
        const search = fields
            .map(f => `${f.key}:*${q}* OR ${f.key}:${q}`)
            .join(" OR ")
        query.push('(' + search + ')');
    }
    return query.join(' AND ') || '*'
}