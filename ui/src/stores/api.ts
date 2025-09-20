import axios from "axios";
import posthog from "posthog-js";
import cloneDeep from "lodash/cloneDeep";
import {defineStore} from "pinia";
import {useMiscStore} from "override/stores/misc";

export const API_URL = "https://api.kestra.io";

interface Feed {
    id: string;
    publicationDate: string;
    href?: string;
    title: string;
    description: string;
    image?: string;
    content: string;
    link: string;
}

interface FeedResponse {
    feeds: Feed[];
    version: string;
}

interface EventData {
    type: string;
    page?: {
        origin?: string;
        path?: string;
        [key: string]: any;
    };
    [key: string]: any;
}

interface State {
    feeds: Feed[];
    version: string | undefined;
    apiConfig: any;
}

let counter = 0;

export const useApiStore = defineStore("api", {
    state: (): State => ({
        feeds: [],
        version: undefined,
        apiConfig: undefined,
    }),

    actions: {
        async loadFeeds(options: { iid: string; uid: string; version: string }) {
            const response = await axios.get<FeedResponse>(`${API_URL}/v1/feeds`, {
                withCredentials: true,
                params: {
                    iid: options.iid,
                    uid: options.uid,
                    version: options.version
                }
            });

            this.feeds = response.data.feeds;
            this.version = response.data.version;

            return response.data;
        },

        async loadConfig() {
            const response = await axios.get(`${API_URL}/v1/config`, {
                withCredentials: true
            });

            this.apiConfig = response.data;
            return response.data;
        },

        async events(data: EventData) {
            const miscStore = useMiscStore();
            const configs = miscStore.configs;
            const uid = localStorage.getItem("uid");

            if (configs === undefined || uid === null || configs["isAnonymousUsageEnabled"] === false) {
                return;
            }

            const additionalData = {
                iid: configs.uuid,
                uid,
                date: new Date().toISOString(),
                counter: counter++,
            };

            const mergeData = {
                ...data,
                ...additionalData
            };

            this.posthogEvents(mergeData);

            return axios.post(`${API_URL}/v1/reports/events`, mergeData, {
                withCredentials: true
            });
        },

        posthogEvents(data: EventData & { date?: string; counter?: number }) {
            const type = data.type;
            const finalData: Partial<EventData> = cloneDeep(data);

            delete finalData.type;
            delete finalData.date;
            delete finalData.counter;

            if (data.page) {
                delete data.page.origin;
                delete data.page.path;
            }

            if (type === "PAGE") {
                posthog.capture("$pageview", finalData);
            } else {
                posthog.capture(data.type.toLowerCase(), finalData);
            }
        },

        async pluginIcons() {
            return axios.get(`${API_URL}/v1/plugins/icons`, {
                withCredentials: true
            });
        }
    }
});