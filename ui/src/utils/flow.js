import permission from "../models/permission";
import action from "../models/action";

export function canSaveFlow(isEdit, user, flow) {
    return (
        isEdit && user &&
        user.isAllowed(permission.FLOW, action.UPDATE, flow.namespace)
    ) || (
        !isEdit && user &&
        user.isAllowed(permission.FLOW, action.CREATE, flow.namespace)
    );
}

export function saveFlow(self, flow) {
    return self.$store
        .dispatch("flow/saveFlow", {
            flow
        })
        .then(() => {
            self.$toast().success(self.$t("flow update ok"));
        })
}
