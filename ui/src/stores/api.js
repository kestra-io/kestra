import axios from "axios";
import posthog from "posthog-js"
import cloneDeep from "lodash/cloneDeep"

let counter = 0;
export const API_URL = "https://api.kestra.io";

export default {
    namespaced: true,
    state: {
        feeds: [],
        version: undefined,
        apiConfig: undefined,
    },

    actions: {
        loadFeeds({commit}, options) {
            return axios.get(API_URL + "/v1/feeds", {
                withCredentials: true,
                params: {
                    iid: options.iid,
                    uid: options.uid,
                    version: options.version
                }
            }).then(response => {
                commit("setFeeds", response.data.feeds)
                commit("setVersion", response.data.version)

                return response.data;
            })
        },
        loadConfig({commit}) {
            return axios.get(API_URL + "/v1/config", {withCredentials: true})
                .then(response => {
                    commit("setApiConfig", response.data)

                    return response.data;
                })
        },
        events({rootGetters, dispatch}, data) {
            let configs = rootGetters["misc/configs"];
            let uid = localStorage.getItem("uid");

            if (configs === undefined || uid === null || configs["isAnonymousUsageEnabled"] === false) {
                return;
            }

            const additionalData = {
                iid: configs.uuid,
                uid: uid,
                date: new Date().toISOString(),
                counter: counter++,
            };

            const mergeData = {
                ...data,
                ...additionalData
            }

            dispatch("posthogEvents", mergeData)

            return axios.post(API_URL + "/v1/reports/events", mergeData, {withCredentials: true});
        },
        posthogEvents(_, data) {
            const type = data.type;
            let finalData = cloneDeep(data);

            delete finalData.type;
            delete finalData.date;
            delete finalData.counter;

            if (data.page) {
                delete data.page.origin;
                delete data.page.path;
            }

            if (type === "PAGE") {
                posthog.capture("$pageview", finalData)
            } else {
                posthog.capture(data.type.toLowerCase(), finalData)
            }
        },
        pluginIcons(_, __) {
            return axios.get(API_URL + "/v1/plugins/icons", {withCredentials: true})
        }
    },
    mutations: {
        setFeeds(state, feeds) {
            state.feeds = feeds
        },
        setVersion(state, version) {
            state.version = version
        },
        setApiConfig(state, config) {
            state.apiConfig = config
        },
    }
}
