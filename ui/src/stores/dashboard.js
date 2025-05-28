 import {apiUrl} from "override/utils/route";

const yamlContentHeader = {
    headers: {
        "Content-Type": "application/x-yaml"
    }
}
export default {
    namespaced: true,
    state: {
        dashboard: undefined,
        chartErrors: []
    },
    actions: {
        list(_, options) {
            const sortString = options.sort ? `&sort=${options.sort}` : ""
            delete options.sort
            return this.$http.get(`${apiUrl(this)}/dashboards?size=100${sortString}`, {
                params: options
            }).then(response => response.data);
        },
        load({commit}, id) {
            return this.$http
                .get(`${apiUrl(this)}/dashboards/${id}`, {
                    validateStatus: (status) => {
                        return status === 200 || status === 404;
                    },
                })
                .then((response) => {
                    let dashboard;

                    if (response.status === 200) dashboard = response.data;
                    else if (response.status === 404) dashboard = {title: "Default", id};

                    commit("setDashboard", dashboard);
                    return dashboard;
                });
        },
        create(_, source) {
            return this.$http.post(`${apiUrl(this)}/dashboards`, source, yamlContentHeader).then(response => response.data);
        },
        update(_, {id, source}) {
            return this.$http.put(`${apiUrl(this)}/dashboards/${id}`, source, yamlContentHeader).then(response => response.data);
        },
        delete(_, id) {
            return this.$http.delete(`${apiUrl(this)}/dashboards/${id}`).then(response => response.data);
        },
       generate(_, {id, chartId, ...filters}) {
            const filtersObj = Object.keys(filters).length > 0 ? filters : null;
            return this.$http
                .post(
                    `${apiUrl(this)}/dashboards/${id}/charts/${chartId}`,
                    filtersObj,
                    {
                        validateStatus: (status) => {
                            return status === 200 || status === 404;
                        },
                    },
                )
                .then((response) => response.data);
        },
        validate(_, source) {
            return this.$http.post(`${apiUrl(this)}/dashboards/validate`, source, yamlContentHeader).then(response => {
                const errors = response.data;
                
                return errors;
            });
        },
        validateChart({commit}, source) {
            return this.$http.post(`${apiUrl(this)}/dashboards/validate/chart`, source, yamlContentHeader).then(response => {
                const errors = response.data;
                commit("setChartErrors", errors);
                return errors;
            });
        },
        chartPreview(_, chart) {
            return this.$http.post(`${apiUrl(this)}/dashboards/charts/preview`, chart)
                .then(response => response.data);
        }
    },
    mutations: {
        setDashboard(state, dashboard) {
            state.dashboard = dashboard
        },
        setChartErrors(state, errors) {
            state.chartErrors = errors
        }
    }
}
