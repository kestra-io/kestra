export default (app, store, router) => {
    const confirmationMessage = app.config.globalProperties.$t("unsaved changed ?");

    window.addEventListener("beforeunload", (e) => {
        if (store.getters["core/unsavedChange"]) {
            (e || window.event).returnValue = confirmationMessage; //Gecko + IE
            return confirmationMessage; //Gecko + Webkit, Safari, Chrome etc.
        }
    });

    const routeEqualsExceptHash = (route1, route2) => {
        const deleteTenantIfEmpty = route => {
            if (route.params.tenant === "") {
                delete route.params.tenant;
            }
        }

        const filteredRouteForEquals = route => ({
            path: route.path,
            query: route.query,
            params: route.params
        })

        deleteTenantIfEmpty(route1);
        deleteTenantIfEmpty(route2);

        return JSON.stringify(filteredRouteForEquals(route1)) === JSON.stringify(filteredRouteForEquals(route2))
    }

    router.beforeEach(async (to, from) => {
        if (store.getters["core/unsavedChange"] && !routeEqualsExceptHash(from, to)) {
            if (confirm(confirmationMessage)) {
                 store.commit("editor/changeOpenedTabs", {
                     action: "dirty",
                     name: "Flow",
                     path: "Flow.yaml",
                     dirty: false,
                });
                store.commit("flow/setFlow", store.getters["flow/lastSavedFlow"]);
                store.commit("core/setUnsavedChange", false);
            } else {
                return false;
            }
        }
    });
}
