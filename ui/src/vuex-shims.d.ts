import type {Store} from "vuex";

declare module "@vue/runtime-core" {
    interface State {
        api:any
        core:any
        flow:any
        template:any
        execution:any
        log:any
        stat:any
        namespace:any
        misc:any
        layout:any
        auth:any
        graph:any
        plugin:any
        taskrun:any
        trigger:any
        editor:any
        doc:any
        bookmarks:any
        dashboard:any
        code:any
    }

    interface ComponentCustomProperties {
        $store: Store<State>;
    }
}