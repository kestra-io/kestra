import {defineStore} from "pinia";

import type {AxiosRequestConfig, AxiosResponse} from "axios";

const header: AxiosRequestConfig = {headers: {"Content-Type": "application/x-yaml"}};
const response: AxiosRequestConfig = {responseType: "blob" as const};
const validateStatus = (status: number) => status === 200 || status === 404;
const downloadHandler = (response: AxiosResponse, filename: string) => {
    const blob = new Blob([response.data], {type: "application/octet-stream"});
    const url = window.URL.createObjectURL(blob);

    Utils.downloadUrl(url, `${filename}.csv`);
};

import {apiUrl} from "override/utils/route";

import Utils from "../utils/utils";

import type {Dashboard, Chart, Request, Parameters} from "../components/dashboard/composables/useDashboards";

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

        async update({id, source}: {id: Dashboard["id"]; source: Dashboard["sourceCode"];}) {
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

        async generate(id: Dashboard["id"], chartId: Chart["id"], parameters: Parameters) {
            const response = await this.$http.post(`${apiUrl(this.vuexStore)}/dashboards/${id}/charts/${chartId}`, parameters, {validateStatus});
            return response.data;
        },

        async validateChart(source: Chart["source"]) {
            const response = await this.$http.post(`${apiUrl(this.vuexStore)}/dashboards/validate/chart`, source, header);
            this.setChartErrors(response.data);
            return response.data;
        },

        async chartPreview(request: Request) {
            const response = await this.$http.post(`${apiUrl(this.vuexStore)}/dashboards/charts/preview`, request);
            return response.data;
        },

        async export(dashboard: Dashboard, chart: Chart, parameters: Parameters) {
            const isDefault = dashboard.id === "default";

            const path = isDefault ? "/charts/export/to-csv" : `/${dashboard.id}/charts/${chart.id}/export/to-csv`;
            const payload = isDefault ? {chart: chart.content, globalFilter: parameters} : parameters;

            const filename = `chart__${chart.id}`;

            return this.$http
                .post(`${apiUrl(this.vuexStore)}/dashboards${path}`, payload, response)
                .then((res) => downloadHandler(res, filename));
        },
    },
});
