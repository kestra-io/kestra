import type {Module} from "vuex";

import axios from "axios";

import type {Dashboard} from "../components/dashboard/composables/useDashboards";
import {apiUrl} from "override/utils/route";

const header = {headers: {"Content-Type": "application/x-yaml"}};
const validateStatus = (status: number) => status === 200 || status === 404;

export interface DashboardState {
    dashboard?: Dashboard;
    chartErrors?: string[];
}

const dashboard: Module<DashboardState, unknown> = {
    namespaced: true,
    state: () => ({
        dashboard: undefined,
        chartErrors: [],
    }),
    mutations: {
        setDashboard(state, dashboard: Dashboard) {
            state.dashboard = dashboard;
        },
        setChartErrors(state, errors: string[]) {
            state.chartErrors = errors;
        },
    },
    actions: {
        list(_, options) {
            const {sort, ...params} = options;

            return axios
                .get(`${apiUrl(this)}/dashboards?size=100${sort ? `&sort=${sort}` : ""}`, {params})
                .then((response) => response.data);
        },
        load({commit}, id) {
            return axios
                .get(`${apiUrl(this)}/dashboards/${id}`, {validateStatus})
                .then((response) => {
                    let dashboard;

                    if (response.status === 200) dashboard = response.data;

                    // If the dashboard is not found, we return a default dashboard with the given id
                    else dashboard = {title: "Default", id};

                    commit("setDashboard", dashboard);

                    return dashboard;
                });
        },
        create(_, source) {
            return axios
                .post(`${apiUrl(this)}/dashboards`, source, header)
                .then((response) => response.data);
        },
        update(_, {id, source}) {
            return axios
                .put(`${apiUrl(this)}/dashboards/${id}`, source, header)
                .then((response) => response.data);
        },
        delete(_, id) {
            return axios
                .delete(`${apiUrl(this)}/dashboards/${id}`)
                .then((response) => response.data);
        },
        generate(_, {id, chartId, ...filters}) {
            return axios
                .post(`${apiUrl(this)}/dashboards/${id}/charts/${chartId}`, Object.keys(filters).length > 0 ? filters : null, {validateStatus})
                .then((response) => response.data);
        },
        validate(_, source) {
            return axios
                .post(`${apiUrl(this)}/dashboards/validate`, source, header)
                .then((response) => response.data);
        },
        validateChart({commit}, source) {
            return axios
                .post(`${apiUrl(this)}/dashboards/validate/chart`, source, header)
                .then((response) => {
                    commit("setChartErrors", response.data);

                    return response.data;
                });
        },
        chartPreview(_, chart) {
            return axios
                .post(`${apiUrl(this)}/dashboards/charts/preview`, chart)
                .then((response) => response.data);
        },
    },
};

export default dashboard;
