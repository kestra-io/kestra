import {defineStore} from "pinia";
import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
import Utils from "../utils/utils";
import {apiUrl} from "override/utils/route";
import {useCoreStore} from "./core";
import {AxiosRequestConfig} from "axios";

export const useTemplateStore = defineStore("template", {
    state: () => ({
        templates: undefined as any[] | undefined,
        template: undefined as any | undefined,
        total: 0,
    }),

    actions: {
        async findTemplates(options: any) {
            const sortString = options.sort ? `?sort=${options.sort}` : "";
            const searchOptions = {...options};
            delete searchOptions.sort;

            const response = await this.$http.get(`${apiUrl()}/templates/search${sortString}`, {
                params: searchOptions
            });

            this.templates = response.data.results;
            this.total = response.data.total;

            return response.data;
        },

        async loadTemplate(options: { namespace: string; id: string }) {
            const response = await this.$http.get(`${apiUrl()}/templates/${options.namespace}/${options.id}`);

            if (response.data.exception) {
                const coreStore = useCoreStore();
                coreStore.message = {
                    title: "Invalid source code",
                    message: response.data.exception,
                    variant: "error"
                };
                delete response.data.exception;
                this.template = JSON.parse(response.data.source);
            } else {
                this.template = response.data;
            }

            return response.data;
        },

        async saveTemplate(options: { template: string }) {
            const template = YAML_UTILS.parse(options.template);
            const response = await this.$http.put(`${apiUrl()}/templates/${template.namespace}/${template.id}`, template);

            if (response.status >= 300) {
                throw new Error("Server error on template save");
            }

            this.template = response.data;
            return response.data;
        },

        async createTemplate(options: { template: string }) {
            const response = await this.$http.post(`${apiUrl()}/templates`, YAML_UTILS.parse(options.template));
            this.template = response.data;
            return response.data;
        },

        async deleteTemplate(template: any) {
            await this.$http.delete(`${apiUrl()}/templates/${template.namespace}/${template.id}`);
            this.template = undefined;
        },

        async exportTemplateByIds(options: { ids: string[] }) {
            const response = await this.$http.post(`${apiUrl()}/templates/export/by-ids`, options.ids, {responseType: "blob"});
            const blob = new Blob([response.data], {type: "application/octet-stream"});
            const url = window.URL.createObjectURL(blob);
            Utils.downloadUrl(url, "templates.zip");
        },

        async exportTemplateByQuery(options: any) {
            const response = await this.$http.get(`${apiUrl()}/templates/export/by-query`, {params: options});
            Utils.downloadUrl(response.request.responseURL, "templates.zip");
        },

        async importTemplates(options: any) {
            return await this.$http.post(`${apiUrl()}/templates/import`, Utils.toFormData(options), {
                headers: {"Content-Type": "multipart/form-data"}
            });
        },

        async deleteTemplateByIds(options: { ids: string[] }) {
            return await this.$http.delete(`${apiUrl()}/templates/delete/by-ids`, {data: options.ids});
        },

        async deleteTemplateByQuery(options: AxiosRequestConfig<any>) {
            return await this.$http.delete(`${apiUrl()}/templates/delete/by-query`, options);
        },
    },
});