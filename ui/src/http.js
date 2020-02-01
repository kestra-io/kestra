import Vue from 'vue';
import VueAxios from 'vue-axios';
import axios from 'axios';
import { debounce } from "throttle-debounce";


export default (callback, store) => {
    const stopLoad = debounce(500, () => {
        store.commit('core/setLoading', false)
    })
    const instance = axios.create({
        timeout: 15000,
        headers: {
            'Content-Type': 'application/json'
        }
    })
    instance.interceptors.request.use(config => {
        store.commit('core/setLoading', true)
        return config

    })
    instance.interceptors.response.use(response => {
        stopLoad()
        return response
    })
    Vue.use(
        VueAxios,
        instance
    );
    Vue.axios.defaults.baseURL = (process.env.VUE_APP_API_URL || "") + "/";
    callback();
};


export const apiRoot = `${process.env.VUE_APP_API_URL}/api/v1/`