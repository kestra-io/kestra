import {defineStore} from "pinia";
import {useBaseNamespacesStore} from "../../composables/useBaseNamespaces";

export const useNamespacesStore = defineStore("namespaces", useBaseNamespacesStore);
