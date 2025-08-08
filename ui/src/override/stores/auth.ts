import {defineStore} from "pinia";

export class Me {
    hasAny(_permission: any, _namespace: any) {
        return true;
    }


    hasAnyAction(_permission: any, _action: any, _namespace?: any) {
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

export const useAuthStore = defineStore("auth", {
    state: () => ({
        user: new Me(),
        isLogged: true,
    }),
    actions: {
        logout(){
            return Promise.resolve(true)
        },
        correction(){
            return Promise.resolve(true)
        }
    },
})
