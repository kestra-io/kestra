import type {Store} from "vuex";

declare module "@vue/runtime-core" {
    interface State {
        namespace:any
        auth:any
    }

    interface ComponentCustomProperties {
        $store: Store<State>;
    }
}