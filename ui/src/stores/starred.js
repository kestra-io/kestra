export default {
    namespaced: true,
    state: {
        pages: localStorage.getItem("starred.pages") ? JSON.parse(localStorage.getItem("starred.pages")) : [],
    },

    mutations: {
        setPages(state, pages) {
            state.pages = pages
        },
    },

    actions: {
        add({commit, state}, page) {
            const pages = state.pages
            if (!pages.find(p => p.path === page.path)) {
                pages.push(page)
                commit("setPages", pages)
                localStorage.setItem("starred.pages", JSON.stringify(pages))
            }
        },
        remove({commit, state}, page) {
            const pages = state.pages
            const index = pages.findIndex(p => p.path === page.path)
            if (index > -1) {
                pages.splice(index, 1)
                commit("setPages", pages)
                localStorage.setItem("starred.pages", JSON.stringify(pages))
            }
        },
    }
}