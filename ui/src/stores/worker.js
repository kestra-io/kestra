import {apiUrl} from "override/utils/route";

export default {
    namespaced: true,

    actions: {
        findAll(_, __) {
            return this.$http.get(`${apiUrl(this)}/workers`).then(response => {
                return response.data;
            })
        }
    }
}
