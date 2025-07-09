import {defineStore} from "pinia";
import {apiUrl} from "override/utils/route";

import type {Dashboard, Chart} from "../components/dashboard/composables/useDashboards";

const header = {headers: {"Content-Type": "application/x-yaml"}};
const validateStatus = (status: number) => status === 200 || status === 404;

export interface State {
    dashboard?: Dashboard;
    chartErrors: string[];
}

export const useDashboardStore = defineStore("dashboard", {
    state: (): State => ({
        dashboard: undefined,
        chartErrors: [],
    }),

    actions: {
        setDashboard(dashboard: Dashboard) {
            this.dashboard = dashboard;
        },

        setChartErrors(errors: string[]) {
            this.chartErrors = errors;
        },

        async list(options: Record<string, any>) {
            const {sort, ...params} = options;
            const response = await this.$http.get(`${apiUrl(this.vuexStore)}/dashboards?size=100${sort ? `&sort=${sort}` : ""}`, {params});

            return response.data;
        },

        async load(id: Dashboard["id"]) {
            const response = await this.$http.get(`${apiUrl(this.vuexStore)}/dashboards/${id}`, {validateStatus});
            let dashboard;

            if (response.status === 200) dashboard = response.data;
            else dashboard = {title: "Default", id};

            this.setDashboard(dashboard);
            return dashboard;
        },

        async create(source: Dashboard["sourceCode"]) {
            const response = await this.$http.post(`${apiUrl(this.vuexStore)}/dashboards`, source, header);
            return response.data;
        },

        async update({id, source}: { id: Dashboard["id"]; source: Dashboard["sourceCode"] }) {
            const response = await this.$http.put(`${apiUrl(this.vuexStore)}/dashboards/${id}`, source, header);
            return response.data;
        },

        async delete(id: Dashboard["id"]) {
            const response = await this.$http.delete(`${apiUrl(this.vuexStore)}/dashboards/${id}`);
            return response.data;
        },

        async validateDashboard(source: Dashboard["sourceCode"]) {
            const response = await this.$http.post(`${apiUrl(this.vuexStore)}/dashboards/validate`, source, header);
            return response.data;
        },

        async generate({id, chartId, ...filters}: {id: Dashboard["id"]; chartId: Chart["id"]; [key: string]: any;}) {
            const response = await this.$http.post(`${apiUrl(this.vuexStore)}/dashboards/${id}/charts/${chartId}`, Object.keys(filters).length > 0 ? filters : null, {validateStatus});
            return response.data;
        },

        async validateChart(source: Chart["source"]) {
            const response = await this.$http.post(`${apiUrl(this.vuexStore)}/dashboards/validate/chart`, source, header);
            this.setChartErrors(response.data);
            return response.data;
        },

        async chartPreview(chart: Chart) {
            const response = await this.$http.post(`${apiUrl(this.vuexStore)}/dashboards/charts/preview`, chart);
            return response.data;
        },
    },
});
