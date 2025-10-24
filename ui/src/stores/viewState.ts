import {defineStore} from "pinia";

type ViewStateMap = Record<string, unknown>;
type ScrollMap = Record<string, number>;

export const useViewStateStore = defineStore("viewState", {
    state: () => ({
        monacoViewStates: {} as ViewStateMap,
        scrollPositions: {} as ScrollMap,
    }),
    actions: {
        saveMonacoViewState(key: string, state: unknown) {
            if (!key) return;
            this.monacoViewStates[key] = state;
        },
        getMonacoViewState<T = unknown>(key: string): T | undefined {
            return key ? (this.monacoViewStates[key] as T | undefined) : undefined;
        },
        saveScrollPosition(key: string, top: number) {
            if (!key) return;
            this.scrollPositions[key] = Math.max(0, Math.floor(top || 0));
        },
        getScrollPosition(key: string): number | undefined {
            return key ? this.scrollPositions[key] : undefined;
        },
        clear(prefix?: string) {
            if (!prefix) {
                this.monacoViewStates = {};
                this.scrollPositions = {};
                return;
            }
            Object.keys(this.monacoViewStates).forEach(k => {
                if (k.startsWith(prefix)) delete this.monacoViewStates[k];
            });
            Object.keys(this.scrollPositions).forEach(k => {
                if (k.startsWith(prefix)) delete this.scrollPositions[k];
            });
        },
    },
});
