import axios from "axios";
import NProgress from "nprogress"

// eslint-disable-next-line no-undef
let root = (process.env.VUE_APP_API_URL || "") + KESTRA_BASE_PATH;
if (!root.endsWith("/")) {
    root = root + "/";
}

// nprogress
let requestsTotal = 0
let requestsCompleted = 0
let latencyThreshold = 0

const progressComplete = () => {
    requestsTotal = 0
    requestsCompleted = 0
    NProgress.done()
}

const initProgress = () => {
    if (0 === requestsTotal) {
        setTimeout(() => NProgress.start(), latencyThreshold)
    }
    requestsTotal++
    NProgress.set(requestsCompleted / requestsTotal)
}

const increaseProgress = () => {
    setTimeout(() => {
        ++requestsCompleted
        if (requestsCompleted >= requestsTotal) {
            progressComplete()
        } else {
            NProgress.set((requestsCompleted / requestsTotal) - 0.1)
        }
    }, latencyThreshold + 50)
}

const requestInterceptor = config => {
    initProgress();
    return config
}

const responseInterceptor = response => {
    increaseProgress();

    return response
}

const errorResponseInterceptor = error => {
    increaseProgress()

    return Promise.reject(error)
}

const progressInterceptor = (progressEvent) => {
    if (progressEvent && progressEvent.loaded && progressEvent.total) {
        NProgress.inc((Math.floor(progressEvent.loaded * 1.0) / progressEvent.total))
    }
}

export default (callback, store, router) => {
    const instance = axios.create({
        timeout: 15000,
        headers: {
            "Content-Type": "application/json"
        },
        onDownloadProgress: progressInterceptor,
        onUploadProgress: progressInterceptor
    })

    instance.interceptors.request.use(requestInterceptor)
    instance.interceptors.response.use(responseInterceptor, errorResponseInterceptor);

    instance.interceptors.response.use(
        response => {
            return response
        }, errorResponse => {
            if (errorResponse.code && errorResponse.code === "ECONNABORTED") {
                store.dispatch("core/showMessage", {
                    content: errorResponse,
                    variant: "danger"
                })

                return Promise.reject(errorResponse);
            }

            if (errorResponse.response.status === 404) {
                store.dispatch("core/showError", errorResponse.response.status)

                return Promise.reject(errorResponse);
            }

            if (errorResponse.response && errorResponse.response.data) {
                store.dispatch("core/showMessage", {
                    content: errorResponse.response.data,
                    variant: "danger"
                })

                return Promise.reject(errorResponse);
            }

            return Promise.reject(errorResponse);
        })

    instance.defaults.baseURL = root;

    instance.defaults.paramsSerializer = {
        indexes: null
    }

    router.beforeEach((to, from, next) => {
        initProgress();

        next()
    })

    router.afterEach((to, from) => {
        increaseProgress();
    })

    callback(instance);
};


export const apiRoot = `${root}api/v1/`
