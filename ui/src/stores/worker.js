import {apiUrlWithoutTenants} from "override/utils/route";

export default {
    namespaced: true,

    actions: {
        findAll(_, __) {
            return this.$http.get(`${apiUrlWithoutTenants(this)}/workers`).then(response => {
                return response.data;
            })
        }
    }
}
