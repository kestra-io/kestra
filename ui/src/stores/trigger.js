import {apiUrl} from "override/utils/route";

export default {
    namespaced: true,

    actions: {
        search({_commit}, options) {
            const sortString = options.sort ? `?sort=${options.sort}` : ""
            delete options.sort
            return this.$http.get(`${apiUrl(this)}/triggers/search${sortString}`, {
                params: options
            }).then(response => {
                return response.data;
            })
        },
        async unlock({_commit}, options) {
            return (await this.$http.post(`${apiUrl(this)}/triggers/${options.namespace}/${options.flowId}/${options.triggerId}/unlock`)).data;
        }
    }
}
