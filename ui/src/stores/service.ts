import {defineStore} from "pinia";
import {apiUrlWithoutTenants} from "override/utils/route";

interface Service {
    id: string;
}

interface State {
    service: Service | undefined;
}

export const useServiceStore = defineStore("service", {
    state: (): State => ({
        service: undefined
    }),

    actions: {
        async findServiceById(options: {id: string}): Promise<Service> {
            const response = await this.$http.get<Service>(`${apiUrlWithoutTenants()}/instance/services/${options.id}`);
            this.service = response.data;
            return response.data;
        }
    }
});
