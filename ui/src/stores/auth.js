class Me {
    // eslint-disable-next-line no-unused-vars
    hasAny(permission, namespace) {
        return true;
    }

    // eslint-disable-next-line no-unused-vars
    hasAnyAction(permission, action, namespace) {
        return true;
    }

    // eslint-disable-next-line no-unused-vars
    isAllowed(permission, action, namespace) {
        return true;
    }

    // eslint-disable-next-line no-unused-vars
    isAllowedGlobal(permission, action) {
        return true;
    }

    // eslint-disable-next-line no-unused-vars
    hasAnyActionOnAnyNamespace(permission, action) {
        return true;
    }

    hasAnyRole() {
        return true;
    }
}

export default {
    namespaced: true,
    state: {
        user: new Me()
    },
    actions: {

    },
    mutations: {
        setUser(state, user) {
            state.user = user
        },
    },
    getters: {
        isLogged: () => {
            return true;
        },
        user: (state) => {
            return state.user;
        }
    }
}
