import { AxiosInstance } from "axios";
import { Router } from "vue-router";

//#region src/index.d.ts
declare global {
  interface Window {
    KESTRA_BASE_PATH: string;
  }
}
declare const createAxios: (oss: boolean, router?: Router, coreStore?: {
  message?: {
    variant?: string;
    response?: any;
    content?: any;
  };
  error?: any;
}, authStore?: {
  isLogged?: boolean;
  logout: () => Promise<void>;
}, beforeLogout?: () => void) => AxiosInstance;
declare function configureAxios(callback: (instance: AxiosInstance) => void, ...args: Parameters<typeof createAxios>): void;
declare function useAxios(): AxiosInstance;
//#endregion
export { configureAxios, useAxios };