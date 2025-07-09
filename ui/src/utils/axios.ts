import axios, {AxiosInstance, AxiosRequestConfig, AxiosResponse, AxiosError, AxiosProgressEvent} from "axios"
import NProgress from "nprogress"
import {Router} from "vue-router"
import {Store} from "vuex"
import {useCoreStore} from "../stores/core"

let pendingRoute = false
let requestsTotal = 0
let requestsCompleted = 0
const latencyThreshold = 0

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

const handleBadResponse = (errorResponse: AxiosError) => {
    const coreStore = useCoreStore()
    coreStore.message = {
        variant: "error",
        response: errorResponse,
        content: errorResponse,
    }
    return Promise.reject(errorResponse)
}

const handleNotFound = (errorResponse: AxiosError) => {
    const coreStore = useCoreStore()
    coreStore.error = errorResponse.response?.status
    return Promise.reject(errorResponse)
}

const handleUnauthorized = (errorResponse: AxiosError, instance: AxiosInstance) => {
    const hasBasicAuthCredentials = localStorage.getItem("basicAuthCredentials") !== null
    
    if (hasBasicAuthCredentials) {
        localStorage.removeItem("basicAuthCredentials")
        delete instance.defaults.headers.common["Authorization"]
    }
    
    return Promise.reject(errorResponse)
}

const handleMessageError = (errorResponse: AxiosError) => {
    const coreStore = useCoreStore()
    coreStore.message = {
        variant: "error",
        response: errorResponse.response,
        content: errorResponse.response?.data
    }
    return Promise.reject(errorResponse)
}

export default (
    callback: (instance: AxiosInstance) => void,
    _store: Store<any>,
    router: Router
): void => {
    const instance: AxiosInstance = axios.create({
        timeout: 15000,
        headers: {"Content-Type": "application/json"},
        onDownloadProgress: progressInterceptor,
        onUploadProgress: progressInterceptor
    })

    instance.interceptors.request.use(config => {
        const basicAuth = localStorage.getItem("basicAuthCredentials")
        if (basicAuth && !config.headers.Authorization) {
            config.headers.Authorization = `Basic ${basicAuth}`
        }
        return requestInterceptor(config)
    })

    instance.interceptors.response.use(responseInterceptor, errorResponseInterceptor)

    instance.interceptors.response.use(
        (response) => response,
        async (errorResponse: AxiosError & QueueItem & {config:{showMessageOnError: boolean}}) => {
            if (errorResponse?.code === "ERR_BAD_RESPONSE" && !errorResponse?.response?.data) {
                return handleBadResponse(errorResponse)
            }

            if (!errorResponse.response) {
                return Promise.reject(errorResponse)
            }

            if (errorResponse.response.status === 404) {
                return handleNotFound(errorResponse)
            }

            if (errorResponse.response.status === 401) {
                return handleUnauthorized(errorResponse, instance)
            }

            if (errorResponse.response.status === 400) {
                return Promise.reject(errorResponse.response.data)
            }

            if (errorResponse.response.data && errorResponse?.config?.showMessageOnError !== false) {
                return handleMessageError(errorResponse)
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

