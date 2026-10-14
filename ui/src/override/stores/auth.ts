import {defineStore} from "pinia"

export class Me {
    hasAny(_permission: string, _namespace?: string) {
        return true
    }


    hasAnyAction(_permission: string, _action: string, _namespace?: string) {
        return true
    }


    isAllowed(_permission: string, _action: string, _namespace?: string) {
        return true
    }


    isAllowedGlobal(_permission: string, _action: string) {
        return true
    }


    hasAnyActionOnAnyNamespace(_permission: string, _action: string) {
        return true
    }

    hasAnyRole() {
        return true
    }

    getNamespacesForAction(_permission: string, _action: string): string[] {
        return []
    }
}

export interface AuthMethods {
    mailsEnabled?: boolean;
    passwordless?: boolean;
    loginPassword?: boolean;
    oauths?: string[];
}

export const useAuthStore = defineStore("auth", {
    state: () => ({
        user: new Me() as Me | undefined,
        isLogged: true,
        auths: undefined as AuthMethods | undefined,
    }),
    actions: {
        logout(){
            return Promise.resolve(true)
        },
        correction(){
            return Promise.resolve(true)
        },
        loadAuths(_options: Record<string, unknown>): Promise<AuthMethods | undefined> {
            return Promise.resolve(undefined)
        },
    },
})
