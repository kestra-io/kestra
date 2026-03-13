import {defineStore} from "pinia";
import {apiUrl} from "override/utils/route";
import {ref} from "vue";
import {useAxios} from "../utils/axios";
import {Message} from "../components/ErrorToast.vue";
import {TUTORIAL_NAMESPACE} from "../utils/constants";
import {Flow} from "./flow";

export const useCoreStore = defineStore("core", () => {
    const message = ref<Message>()
    const error = ref<any>()
    const monacoYamlConfigured = ref(false)
    const tutorialFlows = ref<Flow[]>([])

    const axios = useAxios();

    async function readTutorialFlows() {
        const response = await axios.get(`${apiUrl()}/flows/${TUTORIAL_NAMESPACE}`);
        tutorialFlows.value = response.data;
        return response.data;
    }

    return {
        message,
        error,
        monacoYamlConfigured,
        tutorialFlows,
        readTutorialFlows,
    }
});
