
import "pinia"
import type {AxiosLikeClient} from "@kestra-io/kestra-sdk"
import type {Router} from "vue-router"

declare module "pinia" {

  export interface PiniaCustomProperties {
    $http: AxiosLikeClient

    // type the router added by the plugin above (#adding-new-external-properties)
    $router: Router
  }
}
