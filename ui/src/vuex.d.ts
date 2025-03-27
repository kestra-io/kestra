declare module "vuex"{
    export * from "vuex/types/index.d.ts"
}

declare module "@vue/runtime-core" {
    import {Store} from "vuex";
    interface ComponentCustomProperties {
        $store: Store;
    }
}