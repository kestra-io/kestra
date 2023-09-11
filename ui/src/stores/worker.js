export default {
    namespaced: true,

    actions: {
        findAll(_, __) {
            return this.$http.get(`/api/v1/workers`).then(response => {
                return response.data;
            })
        }
    }
}
