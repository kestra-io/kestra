
import "pinia"
import {AxiosInstance} from "axios"

declare module "pinia" {

  export interface PiniaCustomProperties {

    $http: AxiosInstance

    // type the router added by the plugin above (#adding-new-external-properties)
    $router: Router
  }
}
