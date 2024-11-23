const LOCAL_STORAGE_KEY = "starred.bookmarks"

export default {
    namespaced: true,
    state: {
        pages: localStorage.getItem(LOCAL_STORAGE_KEY) ? JSON.parse(localStorage.getItem(LOCAL_STORAGE_KEY)) : [],
    },

    mutations: {
        setPages(state, pages) {
            state.pages = pages
        },
    },

    actions: {
        add({dispatch, state}, page) {
            const pages = state.pages
            if (!pages.find(p => p.path === page.path)) {
                pages.push(page)
                dispatch("updateAll", pages)
            }
        },
        remove({dispatch, state}, page) {
            const pages = state.pages
            const index = pages.findIndex(p => p.path === page.path)
            if (index > -1) {
                pages.splice(index, 1)
                dispatch("updateAll", pages)
            }
        },
        rename({dispatch, state}, page) {
            const pages = state.pages
            const index = pages.findIndex(p => p.path === page.path)
            if (index > -1) {
                pages.splice(index, 1, {
                    ...pages[index],
                    label: page.label
                })
                dispatch("updateAll", pages)
            }

        },
        updateAll({commit}, pages) {
            commit("setPages", pages)
            localStorage.setItem(LOCAL_STORAGE_KEY, JSON.stringify(pages))
        }
    },
}