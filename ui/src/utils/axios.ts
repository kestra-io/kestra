import axios, {AxiosInstance, AxiosRequestConfig, AxiosResponse, AxiosError, AxiosProgressEvent} from "axios";
import NProgress from "nprogress";
import {Router} from "vue-router";
import {Store} from "vuex";
import {storageKeys} from "./constants";
import {useLayoutStore} from "../stores/layout";
import {useCoreStore} from "../stores/core";

// nprogress
let pendingRoute = false;
let requestsTotal = 0;
let requestsCompleted = 0;
const latencyThreshold = 0;

const JWT_REFRESHED_QUERY = "__jwt_refreshed__";

const progressComplete = () => {
    pendingRoute = false;
    requestsTotal = 0;
    requestsCompleted = 0;
    NProgress.done();
}

const initProgress = () => {
    requestsTotal++;
    if (0 === requestsTotal) {
        setTimeout(() => {
            NProgress.start();
            NProgress.set(requestsCompleted / requestsTotal);
        }, latencyThreshold);
    } else {
        NProgress.set(requestsCompleted / requestsTotal);
    }
}

const increaseProgress = () => {
    setTimeout(() => {
        ++requestsCompleted;
        if (requestsCompleted >= requestsTotal) {
            progressComplete();
        } else {
            NProgress.set((requestsCompleted / requestsTotal) - 0.1);
        }
    }, latencyThreshold + 50);
}

const responseInterceptor = (response: AxiosResponse): AxiosResponse => {
    increaseProgress();
    return response;
}

const errorResponseInterceptor = (error: AxiosError): Promise<AxiosError> => {
    increaseProgress();
    return Promise.reject(error);
}


const progressInterceptor = (progressEvent: AxiosProgressEvent) => {
    if (progressEvent && progressEvent.loaded && progressEvent.total) {
        NProgress.inc((Math.floor(progressEvent.loaded * 1.0) / progressEvent.total));
    }
}

interface QueueItem {
    config: AxiosRequestConfig;
    resolve: (value: AxiosResponse | Promise<AxiosResponse>) => void;
    reject: (reason?: any) => void;
}

export default (
    callback: (instance: AxiosInstance) => void,
    store: Store<any>,
    router: Router
): void => {
    const instance: AxiosInstance = axios.create({
        timeout: 15000,
        headers: {
            "Content-Type": "application/json"
        },
        onDownloadProgress: progressInterceptor,
        onUploadProgress: progressInterceptor
    });

    instance.interceptors.request.use((config) => {
        initProgress();
        return config;
    });
    instance.interceptors.response.use(responseInterceptor, errorResponseInterceptor);

    let toRefreshQueue: QueueItem[] = [];
    let refreshing = false;

    instance.interceptors.response.use(
        (response: AxiosResponse) => {
            return response;
        },
        async (errorResponse: AxiosError & { config?: { showMessageOnError?: boolean } }) => {
            if (errorResponse?.code === "ERR_BAD_RESPONSE" && !errorResponse?.response?.data) {
                const coreStore = useCoreStore();
                coreStore.message = {
                    variant: "error",
                    response: errorResponse,
                    content: errorResponse,
                };
                return Promise.reject(errorResponse);
            }

            if (errorResponse.response === undefined) {
                return Promise.reject(errorResponse);
            }

            if (errorResponse.response.status === 404) {
                const coreStore = useCoreStore();
                coreStore.error = errorResponse.response.status;

                return Promise.reject(errorResponse);
            }

            if (errorResponse.response.status === 401
                && !store.getters["auth/isLogged"]) {
                const base_path = window.KESTRA_BASE_PATH.endsWith("/") ? window.KESTRA_BASE_PATH.slice(0, -1) : window.KESTRA_BASE_PATH;

                if (window.location.pathname.startsWith(base_path + "/ui/login")) {
                    return Promise.reject(errorResponse);
                }

                window.location.assign(`${base_path}/ui/login?from=${window.location.pathname +
                (window.location.search ?? "")}`)
            }

            const impersonate = localStorage.getItem(storageKeys.IMPERSONATE);

            // Authentication expired
            if (errorResponse.response.status === 401 &&
                store.getters["auth/isLogged"] &&
                !document.cookie.split("; ").map(cookie => cookie.split("=")[0]).includes("JWT")
                && !impersonate) {
                // Keep original request
                const originalRequest = errorResponse.config

                if(!originalRequest) {
                    return Promise.reject(errorResponse);
                }

                if (!refreshing) {
                    const originalRequestData = typeof originalRequest.data === "string"
                        ? JSON.parse(originalRequest.data)
                        : (originalRequest.data ?? {});

                    // if we already tried refreshing the token,
                    // the user simply does not have access to this feature
                    if (originalRequestData[JWT_REFRESHED_QUERY] === 1) {
                        return Promise.reject(errorResponse)
                    }

                    refreshing = true;
                    try {
                        await instance.post("/oauth/access_token?grant_type=refresh_token", null, {headers: {"Content-Type": "application/json"}});
                        toRefreshQueue.forEach(({config, resolve, reject}) => {
                            instance.request(config).then(response => {
                                resolve(response)
                            }).catch(error => {
                                reject(error)
                            })
                        })
                        toRefreshQueue = [];
                        refreshing = false;

                        originalRequestData[JWT_REFRESHED_QUERY] = 1;
                        originalRequest.data = JSON.stringify(originalRequestData);
                        return instance(originalRequest)
                    } catch {
                        document.body.classList.add("login");
                        useCoreStore().unsavedChange = false;
                        
                        useLayoutStore().setTopNavbar(undefined);
                        
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
                    // @ts-expect-error https://github.com/kestra-io/kestra-ee/issues/4157
                    toRefreshQueue.push(originalRequest);

                    return;
                }
            }

            if (errorResponse.response.status === 400) {
                return Promise.reject(errorResponse.response.data)
            }

            if (errorResponse.response.data && errorResponse?.config?.showMessageOnError !== false) {
                const coreStore = useCoreStore();
                coreStore.message = {
                    variant: "error",
                    response: errorResponse.response,
                    content: errorResponse.response.data
                };

                return Promise.reject(errorResponse);
            }

            return Promise.reject(errorResponse);
        });

    instance.defaults.paramsSerializer = {
        indexes: null
    };

    router.beforeEach((to, from, next) => {
        if (pendingRoute) {
            requestsTotal--;
        }
        pendingRoute = true;
        initProgress();

        next();
    });

    router.afterEach(() => {
        if (pendingRoute) {
            increaseProgress();
            pendingRoute = false;
        }
    });

    callback(instance);
};
