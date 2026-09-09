import "axios";

declare module "axios" {
  export interface AxiosRequestConfig {
    showMessageOnError?: boolean;
    ignoreNotFound?: boolean;
    skipAuthErrorHandling?: boolean;
  }
}
