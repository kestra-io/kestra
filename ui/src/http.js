import Vue from "vue";
import VueAxios from "vue-axios";
import axios from "axios";

// eslint-disable-next-line no-undef
let root = (process.env.VUE_APP_API_URL || "") + KESTRA_BASE_PATH;
if (!root.endsWith("/")) {
    root = root + "/";
}

export default (callback, store, nprogress) => {
    const instance = axios.create({
        timeout: 15000,
        headers: {
            "Content-Type": "application/json"
        },
        onUploadProgress: function (progressEvent) {
            if (progressEvent && progressEvent.loaded && progressEvent.total) {
                const percent = Math.round((progressEvent.loaded / progressEvent.total) * 100) / 100;
                nprogress.set(percent - 0.10);
            }
        }
    })

    instance.interceptors.response.use(
        response => {
            return response
        }, errorResponse => {
            if (errorResponse.response && errorResponse.response.data) {
                store.dispatch("core/showMessage", {
                    content: errorResponse.response.data,
                    variant: "danger"
                })
            }

            return Promise.reject(errorResponse);
        })

    Vue.use(
        VueAxios,
        instance
    );

    Vue.axios.defaults.baseURL = root;
    callback();
};


export const apiRoot = `${root}api/v1/`
