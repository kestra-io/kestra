import {nextTick} from "vue";
import _isEqual from "lodash/isEqual";

export const pageFromRoute = (route) => {
    return {
        origin: window.location.origin,
        path: route.path,
        params: Object.keys(route.params)
            .map((key) => ({key: key, value: route.params[key]})),
        queries: Object.keys(route.query)
            .map((key) => {
                return {key: key, values: (route.query[key] instanceof Array ? route.query[key] : [route.query[key]])}
            }),
        name: route.name,
        hash: route.hash !== "" ? route.hash : undefined,
    }
}

export default (app, store, router) => {
    router.afterEach((to, from) => {
        nextTick().then(() => {
            if (_isEqual(from, to)) {
                return;
            }
            store.dispatch("api/events", {
                type: "PAGE",
                page: pageFromRoute(to)
            });
        });
    });
}
