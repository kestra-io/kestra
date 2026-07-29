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

    // Same page = same resolved path, ignoring query string (which the block
    // editor uses for transient UI state: open tabs, doc panel, collapsed
    // panels). Comparing path strings is order-independent, unlike a
    // JSON.stringify of the params object whose key order can vary between
    // from/to and spuriously trip the guard.
    //
    // Contract: any feature that sets `unsavedChangesStore.unsavedChange = true`
    // must encode what is being edited in the PATH, never in the query — a
    // query-only change is treated as staying on the same page and will not
    // prompt to save.
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
