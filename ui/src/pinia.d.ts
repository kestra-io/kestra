
import "pinia"
import type {setupKestraAxios} from "./utils/kestraAxios"

declare module "pinia" {

  export interface PiniaCustomProperties {
    $http: ReturnType<typeof setupKestraAxios>

    // type the router added by the plugin above (#adding-new-external-properties)
    $router: Router
  }
}
