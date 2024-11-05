import {apiUrl} from "override/utils/route";

const yamlContentHeader = {
    headers: {
        "Content-Type": "application/x-yaml"
    }
}
export default {
    namespaced: true,
    actions: {
        list(_, options) {
            const sortString = options.sort ? `?sort=${options.sort}` : ""
            delete options.sort
            return this.$http.get(`${apiUrl(this)}/dashboards${sortString}`, {
                params: options
            }).then(response => response.data);
        },
        load(_, id) {
            return this.$http.get(`${apiUrl(this)}/dashboards/${id}`).then(response => response.data);
        },
        create(_, source) {
            return this.$http.post(`${apiUrl(this)}/dashboards`, source, yamlContentHeader).then(response => response.data);
        },
        update(_, {id, source}) {
            return this.$http.put(`${apiUrl(this)}/dashboards/${id}`, source, yamlContentHeader).then(response => response.data);
        },
        delete(_, id) {
            return this.$http.delete(`${apiUrl(this)}/dashboards/${id}`).then(response => response.data);
        }
    }
}
