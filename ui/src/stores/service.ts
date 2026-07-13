import {defineStore} from "pinia"
import * as ServicesAPI from "@kestra-io/kestra-sdk/services"
import type {ServiceInstance} from "@kestra-io/kestra-sdk"

interface State {
    service: ServiceInstance | undefined;
}

export const useServiceStore = defineStore("service", {
    state: (): State => ({
        service: undefined,
    }),

    actions: {
        async findServiceById(options: {id: string}): Promise<ServiceInstance> {
            const service = await ServicesAPI.service(options)
            this.service = service
            return service
        },
    },
})
