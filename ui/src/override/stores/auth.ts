import {defineStore} from "pinia"
import {ref} from "vue"

export class Me {
    hasAny(_permission: any, _namespace?: any) {
        return true
    }


    hasAnyAction(_permission: any, _action: any, _namespace?: any) {
        return true
    }


    isAllowed(_permission: any, _action: any, _namespace: any) {
        return true
    }


    isAllowedGlobal(_permission: any, _action: any) {
        return true
    }


    hasAnyActionOnAnyNamespace(_permission: any, _action: any) {
        return true
    }

    hasAnyRole() {
        return true
    }

    getNamespacesForAction(_permission: any, _action: any): string[] {
        return []
    }
}

export interface AuthMethods {
    mailsEnabled?: boolean;
    passwordless?: boolean;
    loginPassword?: boolean;
    oauths?: string[];
}

export const useAuthStore = defineStore("auth", () => {
    const user = ref<Me | undefined>(new Me())
    const isLogged = ref(true)
    const auths = ref<AuthMethods | undefined>(undefined)

    const logout = async () => {
        return true
    }

    const correction = async () => {
        return true
    }

    function loadAuths(_options: any): Promise<AuthMethods | undefined> {
        return Promise.resolve(undefined)
    }

    return {
        user,
        isLogged,
        auths,
        logout,
        correction,
        loadAuths,
    }
})
