export default (app, store, router) => {
    const confirmationMessage = app.config.globalProperties.$t("unsaved changed ?");

    window.addEventListener("beforeunload", (e) => {
        if (store.getters["core/unsavedChange"]) {
            (e || window.event).returnValue = confirmationMessage; //Gecko + IE
            return confirmationMessage; //Gecko + Webkit, Safari, Chrome etc.
        }
    });

    router.beforeEach(async () => {
        if (store.getters["core/unsavedChange"]) {
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
