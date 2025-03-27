import {Store} from "vuex/types/index.d.ts";

declare module "vuex"{
    export * from "vuex/types/index.d.ts"
}

declare module "vue" {
    interface ComponentCustomProperties {
        $store: Store;
    }
}