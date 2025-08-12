import {ref} from "vue";
import {apiUrl} from "override/utils/route";
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
    const datatypeNamespaces = ref<any[] | undefined>(undefined);
    const addKvModalVisible = ref(false);

    async function loadNamespacesForDatatype(this: any, options: {dataType: string}) {
        const response = await this.$http.get(`${apiUrl(this.vuexStore)}/${options.dataType}s/distinct-namespaces`);
        datatypeNamespaces.value = response.data;
        return response.data;
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

    async function loadInheritedSecrets(this: any, {id, commit: shouldCommit, ...params}: {id: string; commit?: boolean; [key: string]: any}) {
        const response = await this.$http.get(`${apiUrl(this.vuexStore)}/namespaces/${id}/inherited-secrets`, {
            ...VALIDATE,
            params
        });
        if (shouldCommit !== false) {
            inheritedSecrets.value = response.data;
        }
        return response.data;
    }

    async function listSecrets(this: any, {id, commit: shouldCommit, ...params}: {id: string; commit?: boolean; [key: string]: any}) {
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

    async function createSecrets(this: any, payload: {namespace: string; secret: any}) {
        return this.$http.post(`${apiUrl(this.vuexStore)}/namespaces/${payload.namespace}/secrets`, payload.secret);
    }

    async function patchSecret(this: any, payload: {namespace: string; secret: any}) {
        return this.$http.patch(`${apiUrl(this.vuexStore)}/namespaces/${payload.namespace}/secrets/${payload.secret.key}`, payload.secret);
    }

    async function deleteSecrets(this: any, payload: {namespace: string; key: string}) {
        return this.$http.delete(`${apiUrl(this.vuexStore)}/namespaces/${payload.namespace}/secrets/${payload.key}`);
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
        namespace,
        namespaces,
        secrets,
        inheritedSecrets,
        kvs,
        datatypeNamespaces,
        addKvModalVisible,

        loadNamespacesForDatatype,
        kvsList,
        kv,
        createKv,
        deleteKv,
        deleteKvs,
        loadInheritedSecrets,
        listSecrets,
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
