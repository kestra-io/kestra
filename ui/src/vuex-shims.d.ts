import type {Store} from "vuex";

declare module "@vue/runtime-core" {
    interface State {
        core:any
        flow:any
        template:any
        execution:any
        stat:any
        namespace:any
        misc:any
        layout:any
        auth:any
        graph:any
        taskrun:any
        trigger:any
        editor:any
        doc:any
        dashboard:any
        code:any
        blueprints:any
    }

    interface ComponentCustomProperties {
        $store: Store<State>;
    }
}