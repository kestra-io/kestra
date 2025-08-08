import type {Store} from "vuex";

declare module "@vue/runtime-core" {
    interface State {
        auth:any
    }

    interface ComponentCustomProperties {
        $store: Store<State>;
    }
}