import {defineStore} from "pinia";
import {apiUrl} from "override/utils/route";

interface TriggerSearchOptions {
    sort?: string;
    [key: string]: any;
}

interface TriggerFindOptions {
    namespace: string;
    flowId: string;
    [key: string]: any;
}

interface TriggerUpdateOptions {
    [key: string]: any;
}

interface TriggerBackfillOptions {
    [key: string]: any;
}

interface TriggerUnlockOptions {
    namespace: string;
    flowId: string;
    triggerId: string;
}

interface TriggerRestartOptions {
    namespace: string;
    flowId: string;
    triggerId: string;
}

interface TriggerBulkOptions {
    [key: string]: any;
}

export interface TriggerDeleteOptions {
    id?: string;
    namespace: string;
    flowId: string;
    triggerId: string;
}

export const useTriggerStore = defineStore("trigger", {
    state: () => ({}),

    actions: {
        async search(options: TriggerSearchOptions) {
            const sortString = options.sort ? `?sort=${options.sort}` : "";
            delete options.sort;
            const response = await this.$http.get(`${apiUrl()}/triggers/search${sortString}`, {
                params: options
            });
            return response.data;
        },

        async unlock(options: TriggerUnlockOptions) {
            const response = await this.$http.post(`${apiUrl()}/triggers/${options.namespace}/${options.flowId}/${options.triggerId}/unlock`);
            return response.data;
        },

        async restart(options: TriggerRestartOptions) {
            const response = await this.$http.post(`${apiUrl()}/triggers/${options.namespace}/${options.flowId}/${options.triggerId}/restart`);
            return response.data;
        },

        async find(options: TriggerFindOptions) {
            const response = await this.$http.get(`${apiUrl()}/triggers/${options.namespace}/${options.flowId}`, {params: options});
            return response.data;
        },

        async update(options: TriggerUpdateOptions) {
            const response = await this.$http.put(`${apiUrl()}/triggers`, options);
            return response.data;
        },

        async pauseBackfill(options: TriggerBackfillOptions) {
            const response = await this.$http.put(`${apiUrl()}/triggers/backfill/pause`, options);
            return response.data;
        },

        async unpauseBackfill(options: TriggerBackfillOptions) {
            const response = await this.$http.put(`${apiUrl()}/triggers/backfill/unpause`, options);
            return response.data;
        },

        async deleteBackfill(options: TriggerBackfillOptions) {
            const response = await this.$http.post(`${apiUrl()}/triggers/backfill/delete`, options);
            return response.data;
        },

        async unlockByQuery(options: TriggerBulkOptions) {
            const response = await this.$http.post(`${apiUrl()}/triggers/unlock/by-query`, null, {params: options});
            return response.data;
        },

        async unlockByTriggers(options: TriggerBulkOptions) {
            const response = await this.$http.post(`${apiUrl()}/triggers/unlock/by-triggers`, options);
            return response.data;
        },

        async unpauseBackfillByQuery(options: TriggerBulkOptions) {
            const response = await this.$http.post(`${apiUrl()}/triggers/backfill/unpause/by-query`, null, {params: options});
            return response.data;
        },

        async unpauseBackfillByTriggers(options: TriggerBulkOptions) {
            const response = await this.$http.post(`${apiUrl()}/triggers/backfill/unpause/by-triggers`, options);
            return response.data;
        },

        async pauseBackfillByQuery(options: TriggerBulkOptions) {
            const response = await this.$http.post(`${apiUrl()}/triggers/backfill/pause/by-query`, null, {params: options});
            return response.data;
        },

        async pauseBackfillByTriggers(options: TriggerBulkOptions) {
            const response = await this.$http.post(`${apiUrl()}/triggers/backfill/pause/by-triggers`, options);
            return response.data;
        },

        async deleteBackfillByQuery(options: TriggerBulkOptions) {
            const response = await this.$http.post(`${apiUrl()}/triggers/backfill/delete/by-query`, null, {params: options});
            return response.data;
        },

        async deleteBackfillByTriggers(options: TriggerBulkOptions) {
            const response = await this.$http.post(`${apiUrl()}/triggers/backfill/delete/by-triggers`, options);
            return response.data;
        },

        async setDisabledByQuery(options: TriggerBulkOptions) {
            const response = await this.$http.post(`${apiUrl()}/triggers/set-disabled/by-query`, null, {params: options});
            return response.data;
        },

        async setDisabledByTriggers(options: TriggerBulkOptions) {
            const response = await this.$http.post(`${apiUrl()}/triggers/set-disabled/by-triggers`, options);
            return response.data;
        },

        async delete(options: TriggerDeleteOptions) {
            const response = await this.$http.delete(`${apiUrl()}/triggers/${options.namespace}/${options.flowId}/${options.triggerId}`);
            return response.data;
        },

        async deleteByQuery(options: TriggerBulkOptions) {
            const response = await this.$http.delete(`${apiUrl()}/triggers/delete/by-query`, {params: options});
            return response.data;
        },

        async deleteByTriggers(options: TriggerBulkOptions) {
            const response = await this.$http.delete(`${apiUrl()}/triggers/delete/by-triggers`, {data: options});
            return response.data;
        },

        async exportTriggersAsCSV(options: any) {
            const response = await this.$http.get(`${apiUrl()}/triggers/export/by-query/csv`, {params: options, responseType: "blob"});
            const url = window.URL.createObjectURL(new Blob([response.data]));
            const link = document.createElement("a");
            link.href = url;
            link.setAttribute("download", "triggers.csv");
            document.body.appendChild(link);
            link.click();
            link.remove();
            window.URL.revokeObjectURL(url);
        },
    }
});
