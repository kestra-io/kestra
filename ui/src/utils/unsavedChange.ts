import {RouteLocation, Router} from "vue-router";
import {useCoreStore} from "../stores/core";
import {useUnsavedChangesDialog} from "../composables/useUnsavedChangesDialog";

export default (app: any, router: Router) => {
    const confirmationMessage = app.config.globalProperties.$t("unsaved changed ?");
    const coreStore = useCoreStore();
    const {showDialog} = useUnsavedChangesDialog();

    window.addEventListener("beforeunload", (e) => {
        if (coreStore.unsavedChange) {
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
        if (coreStore.unsavedChange && !routeEqualsExceptHash(from, to)) {
            const shouldLeave = await showDialog();
            if (shouldLeave) {
                coreStore.unsavedChange = false;
                next()
                return;
            } else {
                return false;
            }
        }
        next();
    });
}
