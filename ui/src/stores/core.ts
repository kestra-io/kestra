import {defineStore} from "pinia";
import {apiUrl} from "override/utils/route";

interface GuidedProperties {
    tourStarted: boolean;
    manuallyContinue: boolean;
    template: any;
}

interface Message {
    message?: string;
    type?: string;
    title?: string;
    variant?: string;
    response?: any;
    content?: any;
}

interface State {
    message: Message | undefined;
    error: any;
    unsavedChange: boolean;
    guidedProperties: GuidedProperties;
    monacoYamlConfigured: boolean;
    tutorialFlows: any[];
}

export const useCoreStore = defineStore("core", {
    state: (): State => ({
        message: undefined,
        error: undefined,
        unsavedChange: false,
        guidedProperties: {
            tourStarted: false,
            manuallyContinue: false,
            template: undefined,
        },
        monacoYamlConfigured: false,
        tutorialFlows: [],
    }),

    actions: {
        async readTutorialFlows() {
            const response = await this.$http.get(`${apiUrl(this.vuexStore)}/flows/tutorial`);
            this.tutorialFlows = response.data;
            return response.data;
        },
    },
});
