import axios, {AxiosInstance, AxiosRequestConfig, AxiosResponse, AxiosError, AxiosProgressEvent} from "axios"
import NProgress from "nprogress"
import {Router} from "vue-router"
import {Store} from "vuex"
import {storageKeys} from "./constants"
import {useLayoutStore} from "../stores/layout"
import {useCoreStore} from "../stores/core"
import * as BasicAuth from "../utils/basicAuth"

let pendingRoute = false
let requestsTotal = 0
let requestsCompleted = 0
const latencyThreshold = 0

const JWT_REFRESHED_QUERY = "__jwt_refreshed__"

const progressComplete = () => {
    pendingRoute = false
    requestsTotal = 0
    requestsCompleted = 0
    NProgress.done()
}

const initProgress = () => {
    requestsTotal++
    if (requestsTotal === 1) {
        setTimeout(() => {
            NProgress.start()
            NProgress.set(requestsCompleted / requestsTotal)
        }, latencyThreshold)
    } else {
        NProgress.set(requestsCompleted / requestsTotal)
    }
}

const increaseProgress = () => {
    setTimeout(() => {
        requestsCompleted++
        if (requestsCompleted >= requestsTotal) {
            progressComplete()
        } else {
            NProgress.set((requestsCompleted / requestsTotal) - 0.1)
        }
    }, latencyThreshold + 50)
}

const requestInterceptor = (config: any) => {
    initProgress()
    return config
}

const responseInterceptor = (response: AxiosResponse): AxiosResponse => {
    increaseProgress()
    return response
}

const errorResponseInterceptor = (error: AxiosError): Promise<AxiosError> => {
    increaseProgress()
    return Promise.reject(error)
}

const progressInterceptor = (progressEvent: AxiosProgressEvent) => {
    if (progressEvent?.loaded && progressEvent?.total) {
        NProgress.inc(Math.floor(progressEvent.loaded * 1.0) / progressEvent.total)
    }
}

interface QueueItem {
    config: AxiosRequestConfig
    resolve: (value: AxiosResponse | Promise<AxiosResponse>) => void
}

export default (
    callback: (instance: AxiosInstance) => void,
    store: Store<any>,
    router: Router,
    oss: boolean = false
): void => {
    const instance: AxiosInstance = axios.create({
        timeout: 15000,
        headers: {"Content-Type": "application/json"},
        withCredentials: true,
        onDownloadProgress: progressInterceptor,
        onUploadProgress: progressInterceptor
    })

    instance.interceptors.request.use(config => requestInterceptor(config))

    instance.interceptors.response.use(responseInterceptor, errorResponseInterceptor)

    let toRefreshQueue: QueueItem[] = []
    let refreshing = false

    instance.interceptors.response.use(
        (response) => response,
        async (errorResponse: AxiosError & QueueItem & {config:{showMessageOnError: boolean}}) => {
            if (errorResponse?.code === "ERR_BAD_RESPONSE" && !errorResponse?.response?.data) {
                const coreStore = useCoreStore()
                coreStore.message = {
                    variant: "error",
                    response: errorResponse,
                    content: errorResponse,
                }
                return Promise.reject(errorResponse)
            }

            if (errorResponse.response === undefined) {
                return Promise.reject(errorResponse)
            }

            if (errorResponse.response.status === 404) {
                const coreStore = useCoreStore()
                coreStore.error = errorResponse.response.status
                return Promise.reject(errorResponse)
            }

            if (errorResponse.response.status === 401
                && (oss || !store.getters["auth/isLogged"])) {
                const base_path = window.KESTRA_BASE_PATH.endsWith("/") ? window.KESTRA_BASE_PATH.slice(0, -1) : window.KESTRA_BASE_PATH

                if (window.location.pathname.startsWith(base_path + "/ui/login")) {
                    return Promise.reject(errorResponse)
                }

                window.location.assign(`${base_path}/ui/login?from=${window.location.pathname +
                (window.location.search ?? "")}`)
                return
            }

            const impersonate = localStorage.getItem(storageKeys.IMPERSONATE)

            // Authentication expired
            if (errorResponse.response.status === 401 &&
                store.getters["auth/isLogged"] && !oss &&
                !document.cookie.split("; ").map(cookie => cookie.split("=")[0]).includes("JWT")
                && !impersonate) {

                // Keep original request
                const originalRequest = errorResponse.config

                if(!originalRequest) {
                    return Promise.reject(errorResponse)
                }

                // Prevent refresh attempts on refresh token endpoint itself
                if (originalRequest.url?.includes("/oauth/access_token")) {
                    refreshing = false
                    toRefreshQueue = []

                    document.body.classList.add("login")
                    useCoreStore().unsavedChange = false
                    useLayoutStore().setTopNavbar(undefined)
                    BasicAuth.logout()
                    delete instance.defaults.headers.common["Authorization"]

                    store.dispatch("auth/logout").catch(() => {})

                    const currentPath = window.location.pathname
                    const isLoginPath = currentPath.includes("/login")

                    router.push({
                        name: "login",
                        query: {
                            ...(isLoginPath ? {} : {from: currentPath})
                        }
                    })

                    return Promise.reject(errorResponse)
                }

                if (!refreshing) {
                    const originalRequestData = typeof originalRequest.data === "string"
                        ? JSON.parse(originalRequest.data || "{}")
                        : (originalRequest.data ?? {})

                    // if we already tried refreshing the token,
                    // the user simply does not have access to this feature
                    if (originalRequestData[JWT_REFRESHED_QUERY] === 1) {
                        return Promise.reject(errorResponse)
                    }

                    refreshing = true

                    try {
                        await instance.post("/oauth/access_token?grant_type=refresh_token", null, {
                            headers: {"Content-Type": "application/json"},
                            timeout: 5000
                        })

                        // Process queued requests
                        const queuePromises = toRefreshQueue.map(({config, resolve}) =>
                            instance.request(config).then(resolve).catch(error => {
                                console.warn("Queued request failed after token refresh:", error)
                                throw error
                            })
                        )

                        await Promise.allSettled(queuePromises)
                        toRefreshQueue = []
                        refreshing = false

                        // Retry original request
                        originalRequestData[JWT_REFRESHED_QUERY] = 1
                        originalRequest.data = originalRequest.data ? JSON.stringify(originalRequestData) : undefined

                        return instance(originalRequest)

                    } catch (refreshError) {
                        console.warn("Token refresh failed:", refreshError)

                        refreshing = false
                        toRefreshQueue = []

                        document.body.classList.add("login")
                        useCoreStore().unsavedChange = false
                        useLayoutStore().setTopNavbar(undefined)
                        BasicAuth.logout()
                        delete instance.defaults.headers.common["Authorization"]

                        store.dispatch("auth/logout").catch(() => {})

                        const currentPath = window.location.pathname
                        const isLoginPath = currentPath.includes("/login")

                        router.push({
                            name: "login",
                            query: {
                                ...(isLoginPath ? {} : {from: currentPath})
                            }
                        })

                        return Promise.reject(errorResponse)
                    }
                } else {
                    // Add request to queue with a Promise that resolves when refresh is complete
                    return new Promise((resolve, reject) => {
                        toRefreshQueue.push({
                            config: originalRequest,
                            resolve: (response) => resolve(response)
                        })

                        // Set a timeout for queued requests
                        setTimeout(() => {
                            reject(new Error("Token refresh timeout"))
                        }, 10000)
                    })
                }
            }

            if (errorResponse.response.status === 400) {
                return Promise.reject(errorResponse.response.data)
            }

            if (errorResponse.response.data && errorResponse?.config?.showMessageOnError !== false) {
                const coreStore = useCoreStore()
                coreStore.message = {
                    variant: "error",
                    response: errorResponse.response,
                    content: errorResponse.response.data
                }
                return Promise.reject(errorResponse)
            }

            return Promise.reject(errorResponse);
        });

    instance.defaults.paramsSerializer = {
        indexes: null
    };

    router.beforeEach((_to, _from, next) => {
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
    })

    callback(instance);
};

