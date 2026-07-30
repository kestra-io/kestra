import {RouteLocation, Router} from "vue-router"
import {useUnsavedChangesStore} from "../stores/unsavedChanges"

export default (app: any, router: Router) => {
    const confirmationMessage = app.config.globalProperties.$t("unsaved changed ?")
    const unsavedChangesStore = useUnsavedChangesStore()

    window.addEventListener("beforeunload", (e) => {
        if (unsavedChangesStore.unsavedChange) {
            (e || window.event).returnValue = confirmationMessage //Gecko + IE
            return confirmationMessage //Gecko + Webkit, Safari, Chrome etc.
        }
    })

    const isSamePage = (route1: RouteLocation, route2: RouteLocation) =>
        route1.path === route2.path

    router.beforeEach(async (to, from) => {
        if (unsavedChangesStore.unsavedChange && !isSamePage(from, to)) {
            const shouldLeave = await unsavedChangesStore.showDialog()
            if (shouldLeave) {
                unsavedChangesStore.unsavedChange = false
                return true
            } else {
                return false
            }
        }
    })
}
