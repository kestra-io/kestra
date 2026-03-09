import {defineStore} from "pinia";
import {apiUrl} from "override/utils/route";
import {useAxios} from "../utils/axios";

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

export const useTriggerStore = defineStore("trigger", () => {

    const axios = useAxios();
    
    async function search(options: TriggerSearchOptions) {
        const sortString = options.sort ? `?sort=${options.sort}` : "";
        delete options.sort;
        const response = await axios.get(`${apiUrl()}/triggers/search${sortString}`, {
            params: options
        });
        return response.data;
    }

    async function unlock(options: TriggerUnlockOptions) {
        const response = await axios.post(`${apiUrl()}/triggers/${options.namespace}/${options.flowId}/${options.triggerId}/unlock`);
        return response.data;
    }

    async function restart(options: TriggerRestartOptions) {
        const response = await axios.post(`${apiUrl()}/triggers/${options.namespace}/${options.flowId}/${options.triggerId}/restart`);
        return response.data;
    }

    async function find(options: TriggerFindOptions) {
        const response = await axios.get(`${apiUrl()}/triggers/${options.namespace}/${options.flowId}`, {params: options});
        return response.data;
    }

    async function update(options: TriggerUpdateOptions) {
        const response = await axios.put(`${apiUrl()}/triggers`, options);
        return response.data;
    }

    async function pauseBackfill(options: TriggerBackfillOptions) {
        const response = await axios.put(`${apiUrl()}/triggers/backfill/pause`, options);
        return response.data;
    }

    async function unpauseBackfill(options: TriggerBackfillOptions) {
        const response = await axios.put(`${apiUrl()}/triggers/backfill/unpause`, options);
        return response.data;
    }

    async function deleteBackfill(options: TriggerBackfillOptions) {
        const response = await axios.post(`${apiUrl()}/triggers/backfill/delete`, options);
        return response.data;
    }

    async function unlockByQuery(options: TriggerBulkOptions) {
        const response = await axios.post(`${apiUrl()}/triggers/unlock/by-query`, null, {params: options});
        return response.data;
    }

    async function unlockByTriggers(options: TriggerBulkOptions) {
        const response = await axios.post(`${apiUrl()}/triggers/unlock/by-triggers`, options);
        return response.data;
    }

    async function unpauseBackfillByQuery(options: TriggerBulkOptions) {
        const response = await axios.post(`${apiUrl()}/triggers/backfill/unpause/by-query`, null, {params: options});
        return response.data;
    }

    async function unpauseBackfillByTriggers(options: TriggerBulkOptions) {
        const response = await axios.post(`${apiUrl()}/triggers/backfill/unpause/by-triggers`, options);
        return response.data;
    }

    async function pauseBackfillByQuery(options: TriggerBulkOptions) {
        const response = await axios.post(`${apiUrl()}/triggers/backfill/pause/by-query`, null, {params: options});
        return response.data;
    }

    async function pauseBackfillByTriggers(options: TriggerBulkOptions) {
        const response = await axios.post(`${apiUrl()}/triggers/backfill/pause/by-triggers`, options);
        return response.data;
    }

    async function deleteBackfillByQuery(options: TriggerBulkOptions) {
        const response = await axios.post(`${apiUrl()}/triggers/backfill/delete/by-query`, null, {params: options});
        return response.data;
    }

    async function deleteBackfillByTriggers(options: TriggerBulkOptions) {
        const response = await axios.post(`${apiUrl()}/triggers/backfill/delete/by-triggers`, options);
        return response.data;
    }

    async function setDisabledByQuery(options: TriggerBulkOptions) {
        const response = await axios.post(`${apiUrl()}/triggers/set-disabled/by-query`, null, {params: options});
        return response.data;
    }

    async function setDisabledByTriggers(options: TriggerBulkOptions) {
        const response = await axios.post(`${apiUrl()}/triggers/set-disabled/by-triggers`, options);
        return response.data;
    }

    async function deleteTrigger(options: TriggerDeleteOptions) {
        const response = await axios.delete(`${apiUrl()}/triggers/${options.namespace}/${options.flowId}/${options.triggerId}`);
        return response.data;
    }

    async function deleteByQuery(options: TriggerBulkOptions) {
        const response = await axios.delete(`${apiUrl()}/triggers/delete/by-query`, {params: options});
        return response.data;
    }

    async function deleteByTriggers(options: TriggerBulkOptions) {
        const response = await axios.delete(`${apiUrl()}/triggers/delete/by-triggers`, {data: options});
        return response.data;
    }

    async function exportTriggersAsCSV(options: any) {
        const response = await axios.get(`${apiUrl()}/triggers/export/by-query/csv`, {params: options, responseType: "blob"});
        const url = window.URL.createObjectURL(new Blob([response.data]));
        const link = document.createElement("a");
        link.href = url;
        link.setAttribute("download", "triggers.csv");
        document.body.appendChild(link);
        link.click();
        link.remove();
        window.URL.revokeObjectURL(url);
    }

    return {
        search,
        find,
        update,
        pauseBackfill,
        unpauseBackfill,
        deleteBackfill,
        unlock,
        restart,
        unlockByQuery,
        unlockByTriggers,
        unpauseBackfillByQuery,
        unpauseBackfillByTriggers,
        pauseBackfillByQuery,
        pauseBackfillByTriggers,
        deleteBackfillByQuery,
        deleteBackfillByTriggers,
        setDisabledByQuery,
        setDisabledByTriggers,
        delete: deleteTrigger,
        deleteByQuery,
        deleteByTriggers,
        exportTriggersAsCSV
    }
});
