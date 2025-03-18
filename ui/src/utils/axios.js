import axios from "axios";
import NProgress from "nprogress"
import {storageKeys} from "./constants.js";

// nprogress
let requestsTotal = 0
let requestsCompleted = 0
let latencyThreshold = 0

const JWT_REFRESHED_QUERY = "__jwt_refreshed__";

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

    let toRefreshQueue = [];
    let refreshing = false;

    instance.interceptors.response.use(
        response => {
            return response
        }, async errorResponse => {
            if (errorResponse?.code === "ERR_BAD_RESPONSE" && !errorResponse?.response?.data) {
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

            if (errorResponse.response.status === 401
                && !store.getters["auth/isLogged"]) {
                if (window.location.pathname.startsWith("/ui/login")) {
                    return Promise.reject(errorResponse);
                }

                window.location = `/ui/login?from=${window.location.pathname +
                (window.location.search ?? "")}`
            }

            const impersonate = localStorage.getItem(storageKeys.IMPERSONATE);

            // Authentication expired
            if (errorResponse.response.status === 401 &&
                store.getters["auth/isLogged"] &&
                !document.cookie.split("; ").map(cookie => cookie.split("=")[0]).includes("JWT")
            && !impersonate) {
                // Keep original request
                const originalRequest = errorResponse.config

                if (!refreshing) {
                    const originalRequestData = typeof originalRequest.data === "string"
                        ? JSON.parse(originalRequest.data)
                        : (originalRequest.data ?? {});

                    // if we already tried refreshing the token,
                    // the user simply does not have access to this feature
                    if(originalRequestData[JWT_REFRESHED_QUERY] === 1) {
                        return Promise.reject(errorResponse)
                    }

                    refreshing = true;
                    try {
                        await instance.post("/oauth/access_token?grant_type=refresh_token", null, {headers: {"Content-Type": "application/json"}});
                        toRefreshQueue.forEach(({config, resolve, reject}) => {
                            instance.request(config).then(response => { resolve(response) }).catch(error => { reject(error) })
                        })
                        toRefreshQueue = [];
                        refreshing = false;

                        originalRequestData[JWT_REFRESHED_QUERY] = 1;
                        originalRequest.data = JSON.stringify(originalRequestData);
                        return instance(originalRequest)
                    } catch {
                        document.body.classList.add("login");
                        store.dispatch("core/isUnsaved", false);
                        store.commit("layout/setTopNavbar", undefined);
                        router.push({
                            name: "login",
                            query: {
                                expired: 1,
                                from: window.location.pathname + (window.location.search ?? "")
                            }
                        })
                        refreshing = false;
                    }
                } else {
                    toRefreshQueue.push(originalRequest);

                    return;
                }
            }

            if (errorResponse.response.status === 400) {
                return Promise.reject(errorResponse.response.data)
            }

            if (errorResponse.response.data && errorResponse?.config.showMessageOnError !== false) {
                store.dispatch("core/showMessage", {
                    response: errorResponse.response,
                    content: errorResponse.response.data,
                    variant: "error"
                })

                return Promise.reject(errorResponse);
            }

            return Promise.reject(errorResponse);
        })


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
