import {apiUrl} from "override/utils/route";

export default {
    namespaced: true,

    actions: {
        findServiceById(_, options) {
            return this.$http.get(`${apiUrl(this)}/cluster/services/${options.id}`).then(response => {
                return response.data;
            })
        },
    }
}
