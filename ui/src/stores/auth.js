/* eslint-disable @typescript-eslint/no-unused-vars */

class Me {

    hasAny(permission, namespace) {
        return true;
    }


    hasAnyAction(permission, action, namespace) {
        return true;
    }


    isAllowed(permission, action, namespace) {
        return true;
    }


    isAllowedGlobal(permission, action) {
        return true;
    }


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
