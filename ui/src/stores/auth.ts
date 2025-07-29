export class Me {
    hasAny(_permission: any, _namespace: any) {
        return true;
    }


    hasAnyAction(_permission: any, _action: any, _namespace: any) {
        return true;
    }


    isAllowed(_permission: any, _action: any, _namespace: any) {
        return true;
    }


    isAllowedGlobal(_permission: any, _action: any) {
        return true;
    }


    hasAnyActionOnAnyNamespace(_permission: any, _action: any) {
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
        logout(){
            return true
        }
    },
    mutations: {
        setUser(state: {user: Me}, user: Me) {
            state.user = user
        },
    },
    getters: {
        isLogged: () => {
            return true;
        },
        user: (state: {user: Me}): Me => {
            return state.user;
        }
    }
}
