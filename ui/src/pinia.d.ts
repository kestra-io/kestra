
import "pinia"
import {type configureAxios} from "@kestra-io/kestra-sdk"

declare module "pinia" {

  export interface PiniaCustomProperties {
    $http: ReturnType<typeof configureAxios>

    // type the router added by the plugin above (#adding-new-external-properties)
    $router: Router
  }
}
