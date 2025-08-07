import {defineStore} from "pinia";
import {apiUrl} from "override/utils/route";
import {useBaseNamespacesStore, VALIDATE} from "../../composables/useBaseNamespaces";

export const useNamespacesStore = defineStore("namespaces", () => {
    const ossStore = useBaseNamespacesStore();

    async function loadAutocomplete(this: any, options: {q: string}) {
        return (await search.call(this, {
            q: options.q
        })).results.map(({id}: {id: string}) => id);
    }

    async function search(this: any, options: any) {
        const shouldCommit = options.commit !== false;
        delete options.commit;
        const response = await this.$http.get(`${apiUrl(this.vuexStore)}/namespaces/search`, {params: options, ...VALIDATE});
        if (response.status === 200 && shouldCommit) {
            ossStore.namespaces.value = response.data.results;
        }
        return response.data;
    }

    async function load(this: any, id: string) {
        const response = await this.$http.get(`${apiUrl(this.vuexStore)}/namespaces/${id}`, VALIDATE);
        if (response.status === 200) {
            ossStore.namespace.value = response.data;
        }
        return response.data;
    }

    return {
        ...ossStore,
        loadAutocomplete,
        search,
        load,
    };
});
