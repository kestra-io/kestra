import axios from "axios";
import NProgress from "nprogress"

// eslint-disable-next-line no-undef
let root = (import.meta.env.VITE_APP_API_URL || "") + KESTRA_BASE_PATH;
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
                    response: errorResponse,
                    content: errorResponse,
                    variant: "error"
                })

                return Promise.reject(errorResponse);
            }

            if (errorResponse.response === undefined) {
                return Promise.reject(errorResponse);
            }

            if (errorResponse.response.status === 404) {
                store.dispatch("core/showError", errorResponse.response.status)

                return Promise.reject(errorResponse);
            }

            if (errorResponse.response.status === 401 && (!store.getters["auth/isLogged"] || store.getters["auth/expired"])) {
                window.location = "/ui/login?from=" + window.location.pathname +
                    (window.location.search ? "?" + window.location.search : "")
            }

            if (errorResponse.response.status === 401 &&
                store.getters["auth/isLogged"] &&
                !store.getters["auth/user"].hasAnyPermission() &&
                router.currentRoute.name !== "errors/401"
            ) {
                router.push({name: "errors/401"});

                return Promise.reject(errorResponse);
            }

            if (errorResponse.response.status === 400){
                return Promise.reject(errorResponse.response.data)
            }

            if (errorResponse.response.data) {
                store.dispatch("core/showMessage", {
                    response: errorResponse.response,
                    content: errorResponse.response.data,
                    variant: "error"
                })

                if(errorResponse.response.status === 401 &&
                    store.getters["auth/isLogged"]){
                    store.commit("auth/setExpired", true);
                }

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

    router.afterEach(() => {
        increaseProgress();
    })

    callback(instance);
};


export const apiRoot = `${root}api/v1/`
