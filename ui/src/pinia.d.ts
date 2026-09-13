
import "pinia"
import type {KestraHttpClient} from "./utils/kestraHttp"

declare module "pinia" {

  export interface PiniaCustomProperties {
    $http: KestraHttpClient

    // type the router added by the plugin above (#adding-new-external-properties)
    $router: Router
  }
}
