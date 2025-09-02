
import "pinia"
import {Store} from "vuex"
import {AxiosInstance} from "axios"

declare module "pinia" {

  export interface PiniaCustomProperties {
    // by using a setter we can allow both strings and refs
    vuexStore: Store<any, any, any, any>

    $http: AxiosInstance

    // type the router added by the plugin above (#adding-new-external-properties)
    $router: Router
  }
}
