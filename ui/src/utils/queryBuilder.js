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
        return new Date(parseInt(date)).toISOString();
    }
}
