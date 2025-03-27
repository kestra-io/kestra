import {Store} from "vuex/types/index.d.ts";

declare module "vue" {
    interface ComponentCustomProperties {
        $store: Store;
    }
}