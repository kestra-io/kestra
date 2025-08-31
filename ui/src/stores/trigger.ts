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

export const useTriggerStore = defineStore("trigger", {
    state: () => ({}),

    actions: {
        async search(options: TriggerSearchOptions) {
            const sortString = options.sort ? `?sort=${options.sort}` : "";
            delete options.sort;
            const response = await this.$http.get(`${apiUrl(this.vuexStore)}/triggers/search${sortString}`, {
                params: options
            });
            return response.data;
        },

        async unlock(options: TriggerUnlockOptions) {
            const response = await this.$http.post(`${apiUrl(this.vuexStore)}/triggers/${options.namespace}/${options.flowId}/${options.triggerId}/unlock`);
            return response.data;
        },

        async restart(options: TriggerRestartOptions) {
            const response = await this.$http.post(`${apiUrl(this.vuexStore)}/triggers/${options.namespace}/${options.flowId}/${options.triggerId}/restart`);
            return response.data;
        },

        async find(options: TriggerFindOptions) {
            const response = await this.$http.get(`${apiUrl(this.vuexStore)}/triggers/${options.namespace}/${options.flowId}`, {params: options});
            return response.data;
        },

        async update(options: TriggerUpdateOptions) {
            const response = await this.$http.put(`${apiUrl(this.vuexStore)}/triggers`, options);
            return response.data;
        },

        async pauseBackfill(options: TriggerBackfillOptions) {
            const response = await this.$http.put(`${apiUrl(this.vuexStore)}/triggers/backfill/pause`, options);
            return response.data;
        },

        async unpauseBackfill(options: TriggerBackfillOptions) {
            const response = await this.$http.put(`${apiUrl(this.vuexStore)}/triggers/backfill/unpause`, options);
            return response.data;
        },

        async deleteBackfill(options: TriggerBackfillOptions) {
            const response = await this.$http.post(`${apiUrl(this.vuexStore)}/triggers/backfill/delete`, options);
            return response.data;
        },

        async unlockByQuery(options: TriggerBulkOptions) {
            const response = await this.$http.post(`${apiUrl(this.vuexStore)}/triggers/unlock/by-query`, null, {params: options});
            return response.data;
        },

        async unlockByTriggers(options: TriggerBulkOptions) {
            const response = await this.$http.post(`${apiUrl(this.vuexStore)}/triggers/unlock/by-triggers`, options);
            return response.data;
        },

        async unpauseBackfillByQuery(options: TriggerBulkOptions) {
            const response = await this.$http.post(`${apiUrl(this.vuexStore)}/triggers/backfill/unpause/by-query`, null, {params: options});
            return response.data;
        },

        async unpauseBackfillByTriggers(options: TriggerBulkOptions) {
            const response = await this.$http.post(`${apiUrl(this.vuexStore)}/triggers/backfill/unpause/by-triggers`, options);
            return response.data;
        },

        async pauseBackfillByQuery(options: TriggerBulkOptions) {
            const response = await this.$http.post(`${apiUrl(this.vuexStore)}/triggers/backfill/pause/by-query`, null, {params: options});
            return response.data;
        },

        async pauseBackfillByTriggers(options: TriggerBulkOptions) {
            const response = await this.$http.post(`${apiUrl(this.vuexStore)}/triggers/backfill/pause/by-triggers`, options);
            return response.data;
        },

        async deleteBackfillByQuery(options: TriggerBulkOptions) {
            const response = await this.$http.post(`${apiUrl(this.vuexStore)}/triggers/backfill/delete/by-query`, null, {params: options});
            return response.data;
        },

        async deleteBackfillByTriggers(options: TriggerBulkOptions) {
            const response = await this.$http.post(`${apiUrl(this.vuexStore)}/triggers/backfill/delete/by-triggers`, options);
            return response.data;
        },

        async setDisabledByQuery(options: TriggerBulkOptions) {
            const response = await this.$http.post(`${apiUrl(this.vuexStore)}/triggers/set-disabled/by-query`, null, {params: options});
            return response.data;
        },

        async setDisabledByTriggers(options: TriggerBulkOptions) {
            const response = await this.$http.post(`${apiUrl(this.vuexStore)}/triggers/set-disabled/by-triggers`, options);
            return response.data;
        }
    }
});
