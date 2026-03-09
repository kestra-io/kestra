import {defineStore} from "pinia";

interface State {
    topNavbar: any | undefined;
    envName: string | undefined;
    envColor: string | undefined;
    sideMenuCollapsed: boolean;
}

export const useLayoutStore = defineStore("layout", {
    state: (): State => ({
        topNavbar: undefined,
        envName: localStorage.getItem("envName") || undefined,
        envColor: localStorage.getItem("envColor") || undefined,
        sideMenuCollapsed: (() => {
            if (typeof window === "undefined") {
                return false;
            }

            return localStorage.getItem("menuCollapsed") === "true" || window.matchMedia("(max-width: 768px)").matches;
        })(),
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
        },

        setSideMenuCollapsed(value: boolean) {
            this.sideMenuCollapsed = value;
            localStorage.setItem("menuCollapsed", value ? "true" : "false");

            const htmlElement = document.documentElement;
            htmlElement.classList.toggle("menu-collapsed", value);
            htmlElement.classList.toggle("menu-not-collapsed", !value);
        },
    },
});
