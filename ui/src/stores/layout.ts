import {defineStore} from "pinia";

interface State {
    topNavbar: any | undefined;
    envName: string | undefined;
    envColor: string | undefined;
}

export const useLayoutStore = defineStore("layout", {
    state: (): State => ({
        topNavbar: undefined,
        envName: localStorage.getItem("envName") || undefined,
        envColor: localStorage.getItem("envColor") || undefined
    }),
    getters: {},
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
