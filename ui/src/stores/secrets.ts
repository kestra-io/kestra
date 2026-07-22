import * as SecretsAPI from "@kestra-io/kestra-sdk/secrets"
import {defineStore} from "pinia"

export const useSecretsStore = defineStore("secrets", () => {
    function find(params: Parameters<typeof SecretsAPI.listSecrets>[0]) {
        return SecretsAPI.listSecrets(params)
    }

    return {find}
})
