import {defineStore} from "pinia";

interface State {
    topNavbar: any | undefined;
    envName: string | undefined;
    envColor: string | undefined;
}

export const useLayoutStore = defineStore("layout", {
    state: (): State => ({
        topNavbar: undefined,
        envName: undefined,
        envColor: undefined
    }),

    getters: {
        getEnvName: (state): string | undefined => {
            if (!state.envName) {
                state.envName = localStorage.getItem("envName") || undefined;
            }
            return state.envName;
        },
        getEnvColor: (state): string | undefined => {
            if (!state.envColor) {
                state.envColor = localStorage.getItem("envColor") || undefined;
            }
            return state.envColor;
        },
        getTopNavbar: (state): any | undefined => state.topNavbar
    },

    actions: {
        setTopNavbar(value: any) {
            this.topNavbar = value;
        },

        setEnvName(value: string | undefined) {
            if (value) {
                localStorage.setItem("envName", value);
            } else {
                localStorage.removeItem("envName");
            }
            this.envName = value;
        },

        setEnvColor(value: string | undefined) {
            if (value) {
                localStorage.setItem("envColor", value);
            } else {
                localStorage.removeItem("envColor");
            }
            this.envColor = value;
        }
    }
});
