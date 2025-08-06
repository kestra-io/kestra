import {defineStore} from "pinia";
import {apiUrl} from "override/utils/route";
import Utils from "../../utils/utils";

function base(store: any, namespace: string) {
    return `${apiUrl(store.vuexStore)}/namespaces/${namespace}`;
}

const HEADERS = {headers: {"Content-Type": "multipart/form-data"}};

const slashPrefix = (path: string) => (path.startsWith("/") ? path : `/${path}`);
const safePath = (path: string) => encodeURIComponent(path).replace(/%2C|%2F/g, "/");

const VALIDATE = {validateStatus: (status: number) => status === 200 || status === 404};

interface NamespaceState {
    datatypeNamespaces: any[] | undefined;
    namespaces: any[] | undefined;
    namespace: any | undefined;
    inheritedSecrets: any | undefined;
    secrets: any[] | undefined;
    kvs: any[] | undefined;
    addKvModalVisible: boolean;
}

export const useNamespacesStore = defineStore("namespaces", {
    state: (): NamespaceState => ({
        datatypeNamespaces: undefined,
        namespaces: undefined,
        namespace: undefined,
        inheritedSecrets: undefined,
        secrets: undefined,
        kvs: undefined,
        addKvModalVisible: false
    }),

    actions: {
        async loadAutocomplete(options: {q: string}) {
            return (await this.search({
                q: options.q
            })).results.map(({id}: {id: string}) => id);
        },

        async search(options: any) {
            const shouldCommit = options.commit !== false;
            delete options.commit;
            const response = await this.$http.get(`${apiUrl(this.vuexStore)}/namespaces/search`, {params: options, ...VALIDATE});
            if (response.status === 200 && shouldCommit) {
                this.namespaces = response.data.results;
            }
            return response.data;
        },

        async load(id: string) {
            const response = await this.$http.get(`${apiUrl(this.vuexStore)}/namespaces/${id}`, VALIDATE);
            if (response.status === 200) {
                this.namespace = response.data;
            }
            return response.data;
        },

        async kvsList(item: {id: string}) {
            const response = await this.$http.get(`${apiUrl(this.vuexStore)}/namespaces/${item.id}/kv`, VALIDATE);
            this.kvs = response.data;
            return response.data;
        },

        async kv(payload: {namespace: string; key: string}) {
            const response = await this.$http.get(`${apiUrl(this.vuexStore)}/namespaces/${payload.namespace}/kv/${payload.key}`);
            const data = response.data;
            const contentLength = response.headers?.["content-length"];
            if (contentLength === (data.length + 2).toString()) {
                return `"${data}"`;
            }
            return data;
        },

        async createKv(payload: {namespace: string; key: string; value: any; contentType: string; description: string; ttl: string}) {
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
            return this.kvsList({id: payload.namespace});
        },

        async deleteKv(payload: {namespace: string; key: string}) {
            await this.$http.delete(`${apiUrl(this.vuexStore)}/namespaces/${payload.namespace}/kv/${payload.key}`);
            return this.kvsList({id: payload.namespace});
        },

        async deleteKvs(payload: {namespace: string; request: any}) {
            await this.$http.delete(`${apiUrl(this.vuexStore)}/namespaces/${payload.namespace}/kv`, {
                data: payload.request
            });
            return this.kvsList({id: payload.namespace});
        },

        async inheritedSecrets({id, commit: shouldCommit, ...params}: {id: string; commit?: boolean; [key: string]: any}) {
            const response = await this.$http.get(`${apiUrl(this.vuexStore)}/namespaces/${id}/inherited-secrets`, {
                ...VALIDATE,
                params
            });
            if (shouldCommit !== false) {
                this.inheritedSecrets = response.data;
            }
            return response.data;
        },

        async listSecrets({id, commit: shouldCommit, ...params}: {id: string; commit?: boolean; [key: string]: any}) {
            const response = await this.$http.get(`${apiUrl(this.vuexStore)}/namespaces/${id}/secrets`, {
                ...VALIDATE,
                params
            });
            if (response.status === 200 && shouldCommit !== false) {
                this.secrets = response.data.results;
            }
            if (response.status === 404) {
                return {total: 0, results: [], readOnly: false};
            }
            return response.data;
        },

        async createSecrets(payload: {namespace: string; secret: any}) {
            return this.$http.post(`${apiUrl(this.vuexStore)}/namespaces/${payload.namespace}/secrets`, payload.secret);
        },

        async patchSecret(payload: {namespace: string; secret: any}) {
            return this.$http.patch(`${apiUrl(this.vuexStore)}/namespaces/${payload.namespace}/secrets/${payload.secret.key}`, payload.secret);
        },

        async deleteSecrets(payload: {namespace: string; key: string}) {
            return this.$http.delete(`${apiUrl(this.vuexStore)}/namespaces/${payload.namespace}/secrets/${payload.key}`);
        },

        async createDirectory(payload: {namespace: string; path: string}) {
            const URL = `${base(this, payload.namespace)}/files/directory?path=${slashPrefix(payload.path)}`;
            await this.$http.post(URL);
        },

        async readDirectory(payload: {namespace: string; path?: string}) {
            const URL = `${base(this, payload.namespace)}/files/directory${payload.path ? `?path=${slashPrefix(safePath(payload.path))}` : ""}`;
            const request = await this.$http.get(URL);
            return request.data ?? [];
        },

        async createFile(payload: {namespace: string; path: string; content: string}) {
            const DATA = new FormData();
            const BLOB = new Blob([payload.content], {type: "text/plain"});
            DATA.append("fileContent", BLOB);

            const URL = `${base(this, payload.namespace)}/files?path=${slashPrefix(payload.path)}`;
            await this.$http.post(URL, Utils.toFormData(DATA), HEADERS);
        },

        async readFile(payload: {namespace: string; path: string}) {
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
        },

        async searchFiles(payload: {namespace: string; query: string}) {
            const URL = `${base(this, payload.namespace)}/files/search?q=${payload.query}`;
            const request = await this.$http.get(URL);
            return request.data ?? [];
        },

        async importFileDirectory(payload: {namespace: string; path: string; content: string}) {
            const DATA = new FormData();
            const BLOB = new Blob([payload.content], {type: "text/plain"});
            DATA.append("fileContent", BLOB);

            const URL = `${base(this, payload.namespace)}/files?path=${slashPrefix(safePath(payload.path))}`;
            await this.$http.post(URL, DATA, HEADERS);
        },

        async moveFileDirectory(payload: {namespace: string; old: string; new: string}) {
            const URL = `${base(this, payload.namespace)}/files?from=${slashPrefix(payload.old)}&to=${slashPrefix(payload.new)}`;
            await this.$http.put(URL);
        },

        async renameFileDirectory(payload: {namespace: string; old: string; new: string}) {
            const URL = `${base(this, payload.namespace)}/files?from=${slashPrefix(payload.old)}&to=${slashPrefix(payload.new)}`;
            await this.$http.put(URL);
        },

        async deleteFileDirectory(payload: {namespace: string; path: string}) {
            const URL = `${base(this, payload.namespace)}/files?path=${slashPrefix(payload.path)}`;
            await this.$http.delete(URL);
        },

        async exportFileDirectory(payload: {namespace: string}) {
            const URL = `${base(this, payload.namespace)}/files/export`;
            const request = await this.$http.get(URL);

            const name = payload.namespace + "_files.zip";
            Utils.downloadUrl(request.request.responseURL, name);
        },

        async loadNamespacesForDatatype(options: {dataType: string}) {
            const response = await this.$http.get(`${apiUrl(this.vuexStore)}/${options.dataType}s/distinct-namespaces`);
            this.datatypeNamespaces = response.data;
            return response.data;
        }
    }
});
