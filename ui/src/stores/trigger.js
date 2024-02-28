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
        },
        find({_commit}, options) {
            return this.$http.get(`${apiUrl(this)}/triggers/${options.namespace}/${options.flowId}`).then(response => {
                return response.data;
            })
        },
        update({_commit}, options) {
            return this.$http.put(`${apiUrl(this)}/triggers`, options)
                .then(response => {
                    return response.data;
                })
        },
        pauseBackfill({_commit}, options) {
            return this.$http.put(`${apiUrl(this)}/triggers/backfill/pause`, options)
                .then(response => {
                    return response.data;
                })
        },
        unpauseBackfill({_commit}, options) {
            return this.$http.put(`${apiUrl(this)}/triggers/backfill/unpause`, options)
                .then(response => {
                    return response.data;
                })
        },
        deleteBackfill({_commit}, options) {
            return this.$http.put(`${apiUrl(this)}/triggers/backfill/delete`, options)
                .then(response => {
                    return response.data;
                })
        }
    }
}
