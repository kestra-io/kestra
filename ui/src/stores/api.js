
export default {
    namespaced: true,
    state: {
        feeds: [],
    },

    actions: {
        loadFeeds({commit}, options) {
            return this.$http.get("https://api.kestra.io/v1/feeds/latest", {
                params: {
                    iid: options.iid,
                    uid: options.uid,
                    version: options.version
                }
            }).then(response => {
                commit("setFeeds", response.data)

                return response.data;
            })
        }
    },
    mutations: {
        setFeeds(state, feeds) {
            state.feeds = feeds
        }
    }
}
