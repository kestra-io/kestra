import * as KvAPI from "@kestra-io/kestra-sdk/kv"
import {defineStore} from "pinia"

export const useKvStore = defineStore("kv", () => {
    function find(params: Parameters<typeof KvAPI.listAllKeys>[0]) {
        return KvAPI.listAllKeys(params)
    }

    return {find}
})
