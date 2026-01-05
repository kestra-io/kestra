import {RouteLocation, Router} from "vue-router";
import {useUnsavedChangesStore} from "../stores/unsavedChanges";

export default (app: any, router: Router) => {
    const confirmationMessage = app.config.globalProperties.$t("unsaved changed ?");
    const unsavedChangesStore = useUnsavedChangesStore();

    window.addEventListener("beforeunload", (e) => {
        if (unsavedChangesStore.unsavedChange) {
            (e || window.event).returnValue = confirmationMessage; //Gecko + IE
            return confirmationMessage; //Gecko + Webkit, Safari, Chrome etc.
        }
    });

    const routeEqualsExceptHash = (route1: RouteLocation, route2: RouteLocation) => {
        const deleteTenantIfEmpty = (route: RouteLocation) => {
            if (route.params.tenant === "") {
                delete route.params.tenant;
            }
        }

        const filteredRouteForEquals = (route: RouteLocation) => ({
            path: route.path,
            query: route.query,
            params: route.params
        })

        deleteTenantIfEmpty(route1);
        deleteTenantIfEmpty(route2);

        return JSON.stringify(filteredRouteForEquals(route1)) === JSON.stringify(filteredRouteForEquals(route2))
    }

    router.beforeEach(async (to, from, next) => {
        if (unsavedChangesStore.unsavedChange && !routeEqualsExceptHash(from, to)) {
            const shouldLeave = await unsavedChangesStore.showDialog();
            if (shouldLeave) {
                unsavedChangesStore.unsavedChange = false;
                next()
                return;
            } else {
                next(false);
                return;
            }
        }
        next();
    });
}
