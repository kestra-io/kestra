import {ref} from "vue";
import {apiUrl, apiUrlWithTenant} from "override/utils/route";
import Utils from "../utils/utils";

function base(store: any, namespace: string) {
    return `${apiUrl(store.vuexStore)}/namespaces/${namespace}`;
}

const HEADERS = {headers: {"Content-Type": "multipart/form-data"}};
const slashPrefix = (path: string) => (path.startsWith("/") ? path : `/${path}`);
const safePath = (path: string) => encodeURIComponent(path).replace(/%2C|%2F/g, "/");
export const VALIDATE = {validateStatus: (status: number) => status === 200 || status === 404};

export function useBaseNamespacesStore() {
    const namespace = ref<any>(undefined);
    const namespaces = ref<any[] | undefined>(undefined);
    const secrets = ref<any[] | undefined>(undefined);
    const inheritedSecrets = ref<any>(undefined);
    const kvs = ref<any[] | undefined>(undefined);
    const inheritedKVs = ref<any>(undefined);
    const inheritedKVModalVisible = ref(false);
    const addKvModalVisible = ref(false);
    const autocomplete = ref<any>(undefined);
    const total = ref(0);
    const existing = ref(true);

    async function loadAutocomplete(this: any, options?: {q?: string, ids?: string[], existingOnly?: boolean}) {
        const response = await this.$http.post(`${apiUrlWithTenant(this.vuexStore, this.$router.currentRoute)}/namespaces/autocomplete`, options ?? {});
        autocomplete.value = response.data;
        return response.data;
    }

    async function search(this: any, options: any) {
        const shouldCommit = options.commit !== false;
        delete options.commit;
        const response = await this.$http.get(`${apiUrl(this.vuexStore)}/namespaces/search`, {params: options, ...VALIDATE});
        if (response.status === 200 && shouldCommit) {
            namespaces.value = response.data.results;
            total.value = response.data.total;
        }
        return response.data;
    }

    async function load(this: any, id: string) {
        const response = await this.$http.get(`${apiUrl(this.vuexStore)}/namespaces/${id}`, VALIDATE);

        if(response.status === 200) {
            namespace.value = response.data;
            existing.value = true;
        }

        if(response.status === 404) {
            existing.value = false;
        }

        return response.data;
    }

    async function loadDependencies(this: any, options: {namespace: string}) {
        return await this.$http.get(`${apiUrl(this.vuexStore)}/namespaces/${options.namespace}/dependencies`);
    }

    async function kvsList(this: any, item: {id: string}) {
        const response = await this.$http.get(`${apiUrl(this.vuexStore)}/namespaces/${item.id}/kv`, VALIDATE);
        kvs.value = response.data;
        return response.data;
    }

    async function kv(this: any, payload: {namespace: string; key: string}) {
        const response = await this.$http.get(`${apiUrl(this.vuexStore)}/namespaces/${payload.namespace}/kv/${payload.key}`);
        const data = response.data;
        const contentLength = response.headers?.["content-length"];
        if (contentLength === (data.length + 2).toString()) {
            return `"${data}"`;
        }
        return data;
    }

    async function loadInheritedKVs(this: any, id: string) {
        const response = await this.$http.get(`${apiUrl(this.vuexStore)}/namespaces/${id}/kv/inheritance`, {...VALIDATE});
        inheritedKVs.value = response.data;
    }

    async function createKv(this: any, payload: {namespace: string; key: string; value: any; contentType: string; description: string; ttl: string}) {
        await this.$http.put(
            `${apiUrl(this.vuexStore)}/namespaces/${payload.namespace}/kv/${payload.key}`,
            payload.value,
            {
                headers: {
                    "Content-Type": payload.contentType,
                    "description": payload.description,
                    "ttl": payload.ttl
                }
            }
        );
        return kvsList.call(this, {id: payload.namespace});
    }

    async function deleteKv(this: any, payload: {namespace: string; key: string}) {
        await this.$http.delete(`${apiUrl(this.vuexStore)}/namespaces/${payload.namespace}/kv/${payload.key}`);
        return kvsList.call(this, {id: payload.namespace});
    }

    async function deleteKvs(this: any, payload: {namespace: string; request: any}) {
        await this.$http.delete(`${apiUrl(this.vuexStore)}/namespaces/${payload.namespace}/kv`, {
            data: payload.request
        });
        return kvsList.call(this, {id: payload.namespace});
    }

    async function loadInheritedSecrets(this: any, {id, commit: shouldCommit, ...params}: {id: string; commit: boolean | undefined; [key: string]: any}): Promise<Record<string, string[]>> {
        const response = await this.$http.get(`${apiUrl(this.vuexStore)}/namespaces/${id}/inherited-secrets`, {
            ...VALIDATE,
            params
        });
        if (shouldCommit !== false) {
            inheritedSecrets.value = response.data;
        }
        if (response.status === 404) {
            return {[id]: []}
        }
        return response.data;
    }

    async function listSecrets(this: any, {id, commit: shouldCommit, ...params}: {id: string; commit: boolean | undefined; [key: string]: any}): Promise<{total: number, results: {key: string, description?: string, tags?: string}[], readOnly?: boolean}> {
        const response = await this.$http.get(`${apiUrl(this.vuexStore)}/namespaces/${id}/secrets`, {
            ...VALIDATE,
            params
        });
        if (response.status === 200 && shouldCommit !== false) {
            secrets.value = response.data.results;
        }
        if (response.status === 404) {
            return {total: 0, results: [], readOnly: false};
        }
        return response.data;
    }

    async function usableSecrets(this: ReturnType<typeof useBaseNamespacesStore>, id: string): Promise<string[]> {
        return [
            ...Object.values((await this.loadInheritedSecrets({id, commit: false})) ?? {}).flat(),
            ...(await this.listSecrets({id, commit: false})).results.map(({key}) => key)
        ];
    }

    async function createSecrets(this: any, _: {namespace: string; secret: any}) {
        // NOOP IN OSS
    }

    async function patchSecret(this: any, _: {namespace: string; secret: any}) {
        // NOOP IN OSS
    }

    async function deleteSecrets(this: any, _: {namespace: string; key: string}) {
        // NOOP IN OSS
    }

    async function createDirectory(this: any, payload: {namespace: string; path: string}) {
        const URL = `${base(this, payload.namespace)}/files/directory?path=${slashPrefix(payload.path)}`;
        await this.$http.post(URL);
    }

    async function readDirectory(this: any, payload: {namespace: string; path?: string}) {
        const URL = `${base(this, payload.namespace)}/files/directory${payload.path ? `?path=${slashPrefix(safePath(payload.path))}` : ""}`;
        const request = await this.$http.get(URL);
        return request.data ?? [];
    }

    async function createFile(this: any, payload: {namespace: string; path: string; content: string}) {
        const DATA = new FormData();
        const BLOB = new Blob([payload.content], {type: "text/plain"});
        DATA.append("fileContent", BLOB);

        const URL = `${base(this, payload.namespace)}/files?path=${slashPrefix(payload.path)}`;
        await this.$http.post(URL, Utils.toFormData(DATA), HEADERS);
    }

    async function readFile(this: any, payload: {namespace: string; path: string}) {
        if (!payload.path) return;

        const URL = `${base(this, payload.namespace)}/files?path=${slashPrefix(safePath(payload.path))}`;
        const request = await this.$http.get(URL, {
            ...VALIDATE,
            transformResponse: (response: any) => response, 
            responseType: "json"
        });

        if(request.status === 404) {
            const message = JSON.parse(request.data)?.message;
            console.error(message ?? "File not found");
            return "";
        }

        return request.data ?? "";
    }

    async function searchFiles(this: any, payload: {namespace: string; query: string}) {
        const URL = `${base(this, payload.namespace)}/files/search?q=${payload.query}`;
        const request = await this.$http.get(URL);
        return request.data ?? [];
    }

    async function importFileDirectory(this: any, payload: {namespace: string; path: string; content: string}) {
        const DATA = new FormData();
        const BLOB = new Blob([payload.content], {type: "text/plain"});
        DATA.append("fileContent", BLOB);

        const URL = `${base(this, payload.namespace)}/files?path=${slashPrefix(safePath(payload.path))}`;
        await this.$http.post(URL, DATA, HEADERS);
    }

    async function moveFileDirectory(this: any, payload: {namespace: string; old: string; new: string}) {
        const URL = `${base(this, payload.namespace)}/files?from=${slashPrefix(payload.old)}&to=${slashPrefix(payload.new)}`;
        await this.$http.put(URL);
    }

    async function renameFileDirectory(this: any, payload: {namespace: string; old: string; new: string}) {
        const URL = `${base(this, payload.namespace)}/files?from=${slashPrefix(payload.old)}&to=${slashPrefix(payload.new)}`;
        await this.$http.put(URL);
    }

    async function deleteFileDirectory(this: any, payload: {namespace: string; path: string}) {
        const URL = `${base(this, payload.namespace)}/files?path=${slashPrefix(payload.path)}`;
        await this.$http.delete(URL);
    }

    async function exportFileDirectory(this: any, payload: {namespace: string}) {
        const URL = `${base(this, payload.namespace)}/files/export`;
        const request = await this.$http.get(URL);

        const name = payload.namespace + "_files.zip";
        Utils.downloadUrl(request.request.responseURL, name);
    }

    return {
        autocomplete,
        loadAutocomplete,
        search,
        total,
        load,
        loadDependencies,
        existing,
        namespace,
        namespaces,
        secrets,
        inheritedSecrets,
        kvs,
        inheritedKVModalVisible,
        addKvModalVisible,
        kvsList,
        kv,
        loadInheritedKVs,
        inheritedKVs,
        createKv,
        deleteKv,
        deleteKvs,
        loadInheritedSecrets,
        listSecrets,
        usableSecrets,
        createSecrets,
        patchSecret,
        deleteSecrets,
        createDirectory,
        readDirectory,
        createFile,
        readFile,
        searchFiles,
        importFileDirectory,
        moveFileDirectory,
        renameFileDirectory,
        deleteFileDirectory,
        exportFileDirectory,
    };
}
