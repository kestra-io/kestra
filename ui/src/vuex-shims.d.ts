import type {Store} from "vuex";

declare module "@vue/runtime-core" {
    interface State {
        flow:any
        template:any
        execution:any
        namespace:any
        misc:any
        auth:any
        graph:any
        editor:any
        code:any
    }

    interface ComponentCustomProperties {
        $store: Store<State>;
    }
}