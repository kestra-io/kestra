import axios from "axios";

let counter = 0;
const API_URL = "https://api.kestra.io";

export default {
    namespaced: true,
    state: {
        feeds: [],
        version: undefined,
    },

    actions: {
        loadFeeds({commit}, options) {
            return axios.get(API_URL + "/v1/feeds", {
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
        events({rootGetters}, data) {
            let configs = rootGetters["misc/configs"];
            let uid = localStorage.getItem("uid");

            if (configs === undefined || uid === null || configs["isAnonymousUsageEnabled"] === false) {
                return;
            }

            return axios.post(API_URL + "/v1/reports/events", {
                ...data,
                ...{
                    iid: configs.uuid,
                    uid: uid,
                    date: new Date().toISOString(),
                    counter: counter++,
                }
            });
        },
        pluginIcons(_, __) {
            return axios.get(API_URL + "/v1/plugins/icons", {})
        }
    },
    mutations: {
        setFeeds(state, feeds) {
            state.feeds = feeds
        },
        setVersion(state, version) {
            state.version = version
        }
    }
}
