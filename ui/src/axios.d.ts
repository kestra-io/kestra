import "axios";

declare module "axios" {
  export interface AxiosRequestConfig {
    showMessageOnError?: boolean;
  }
}
