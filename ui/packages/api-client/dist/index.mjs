import axios from "axios";
import NProgress from "nprogress";
import {client} from "@kestra-io/sdk-ts/client.gen";
//#region src/index.ts
let pendingRoute = false;
let requestsTotal = 0;
let requestsCompleted = 0;
const latencyThreshold = 0;
const REFRESHED_HEADER = "X-JWT-Refreshed";
const progressComplete = () => {
	pendingRoute = false;
	requestsTotal = 0;
	requestsCompleted = 0;
	NProgress.done();
};
const initProgress = () => {
	requestsTotal++;
	if (requestsTotal === 1) setTimeout(() => {
		NProgress.start();
		NProgress.set(requestsCompleted / requestsTotal);
	}, latencyThreshold);
	else NProgress.set(requestsCompleted / requestsTotal);
};
const increaseProgress = () => {
	setTimeout(() => {
		requestsCompleted++;
		if (requestsCompleted >= requestsTotal) progressComplete();
		else NProgress.set(requestsCompleted / requestsTotal - .1);
	}, latencyThreshold + 50);
};
const requestInterceptor = (config) => {
	initProgress();
	return config;
};
const responseInterceptor = (response) => {
	increaseProgress();
	return response;
};
const errorResponseInterceptor = (error) => {
	increaseProgress();
	return Promise.reject(error);
};
const progressInterceptor = (progressEvent) => {
	if (progressEvent?.loaded && progressEvent?.total) NProgress.inc(Math.floor(progressEvent.loaded * 1) / progressEvent.total);
};
const createAxios = (oss, router, coreStore, authStore, beforeLogout) => {
	const instance = axios.create({
		timeout: 15e3,
		headers: {"Content-Type": "application/json"},
		withCredentials: true,
		onDownloadProgress: progressInterceptor,
		onUploadProgress: progressInterceptor
	});
	instance.interceptors.request.use((config) => requestInterceptor(config));
	instance.interceptors.response.use(responseInterceptor, errorResponseInterceptor);
	let toRefreshQueue = [];
	let refreshing = false;
	instance.interceptors.response.use((response) => response, async (errorResponse) => {
		if (errorResponse?.code === "ERR_BAD_RESPONSE" && !errorResponse?.response?.data) {
			if (coreStore) coreStore.message = {
				variant: "error",
				response: errorResponse.response,
				content: errorResponse
			};
			return Promise.reject(errorResponse);
		}
		if (errorResponse.response === void 0) return Promise.reject(errorResponse);
		if (errorResponse.response.status === 404) {
			if (coreStore) coreStore.error = errorResponse.response.status;
			return Promise.reject(errorResponse);
		}
		if (errorResponse.response.status === 401 && (oss || !authStore?.isLogged)) {
			const base_path = window.KESTRA_BASE_PATH.endsWith("/") ? window.KESTRA_BASE_PATH.slice(0, -1) : window.KESTRA_BASE_PATH;
			if (window.location.pathname.startsWith(base_path + "/ui/login")) return Promise.reject(errorResponse);
			window.location.assign(`${base_path}/ui/login?from=${window.location.pathname + (window.location.search ?? "")}`);
			return;
		}
		const impersonate = window.sessionStorage.getItem("impersonate");
		if (errorResponse.response.status === 401 && authStore?.isLogged && !oss && !document.cookie.split("; ").map((cookie) => cookie.split("=")[0]).includes("JWT") && !impersonate) {
			const originalRequest = errorResponse.config;
			if (!originalRequest) return Promise.reject(errorResponse);
			if (originalRequest.url?.includes("/oauth/access_token")) {
				refreshing = false;
				toRefreshQueue = [];
				beforeLogout?.();
				delete instance.defaults.headers.common["Authorization"];
				authStore?.logout().catch(() => {});
				const currentPath = window.location.pathname;
				const isLoginPath = currentPath.includes("/login");
				router?.push({
					name: "login",
					query: isLoginPath ? {} : {from: currentPath}
				});
				return Promise.reject(errorResponse);
			}
			if (!refreshing) {
				if (originalRequest.headers[REFRESHED_HEADER] === "1") return Promise.reject(errorResponse);
				refreshing = true;
				try {
					await instance.post("/oauth/access_token?grant_type=refresh_token", null, {
						headers: {"Content-Type": "application/json"},
						timeout: 5e3
					});
					const queuePromises = toRefreshQueue.map(({config, resolve}) => instance.request(config).then(resolve).catch((error) => {
						console.warn("Queued request failed after token refresh:", error);
						throw error;
					}));
					await Promise.allSettled(queuePromises);
					toRefreshQueue = [];
					refreshing = false;
					originalRequest.headers[REFRESHED_HEADER] = "1";
					return instance(originalRequest);
				} catch (refreshError) {
					console.warn("Token refresh failed:", refreshError);
					refreshing = false;
					toRefreshQueue = [];
					beforeLogout?.();
					delete instance.defaults.headers.common["Authorization"];
					authStore?.logout().catch(() => {});
					const currentPath = window.location.pathname;
					const isLoginPath = currentPath.includes("/login");
					router?.push({
						name: "login",
						query: isLoginPath ? {} : {from: currentPath}
					});
					return Promise.reject(errorResponse);
				}
			} else return new Promise((resolve, reject) => {
				toRefreshQueue.push({
					config: originalRequest,
					resolve: (response) => resolve(response)
				});
				setTimeout(() => {
					reject(/* @__PURE__ */ new Error("Token refresh timeout"));
				}, 1e4);
			});
		}
		if (errorResponse.response.status === 400) return Promise.reject(errorResponse.response.data);
		if (errorResponse.response.data && errorResponse?.config?.showMessageOnError !== false) {
			if (coreStore) coreStore.message = {
				variant: "error",
				response: errorResponse.response,
				content: errorResponse.response.data
			};
			return Promise.reject(errorResponse);
		}
		return Promise.reject(errorResponse);
	});
	instance.defaults.paramsSerializer = {indexes: null};
	router?.beforeEach((_to, _from, next) => {
		if (pendingRoute) requestsTotal--;
		pendingRoute = true;
		initProgress();
		next();
	});
	router?.afterEach(() => {
		if (pendingRoute) {
			increaseProgress();
			pendingRoute = false;
		}
	});
	client.setConfig({axios: instance});
	return instance;
};
let axiosInstance = null;
function configureAxios(callback, ...args) {
	if (!axiosInstance) axiosInstance = createAxios(...args);
	callback(axiosInstance);
}
function useAxios() {
	return new Proxy({}, {get(_target, prop) {
		if (!axiosInstance) throw new Error("Axios instance not initialized. Please call configureAxios first.");
		const value = axiosInstance[prop];
		return typeof value === "function" ? value.bind(axiosInstance) : value;
	}});
}
//#endregion
export {configureAxios, useAxios};
