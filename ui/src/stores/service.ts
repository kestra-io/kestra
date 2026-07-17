import {defineStore} from "pinia"
import {ref} from "vue"
import * as ServicesAPI from "@kestra-io/kestra-sdk/services"
import type {ServiceInstance} from "@kestra-io/kestra-sdk"

export const useServiceStore = defineStore("service", () => {
    const service = ref<ServiceInstance | undefined>(undefined)

    async function findServiceById(options: {id: string}): Promise<ServiceInstance> {
        const result = await ServicesAPI.service(options)
        service.value = result
        return result
    }

    return {
        service,
        findServiceById,
    }
})
