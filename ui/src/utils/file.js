import action from "../models/action";
import permission from "../models/permission";

export function canSaveFile(isEdit, user, file, dataType) {
    const typedPermission = permission[dataType.toUpperCase()]
    return (
        isEdit && user &&
        user.isAllowed(typedPermission, action.UPDATE, file.namespace)
    ) || (
        !isEdit && user &&
        user.isAllowed(typedPermission, action.CREATE, file.namespace)
    );
}

export function saveFile(self, file, dataType) {
    return self.$store
        .dispatch(`${dataType}/${dataType.toUpperCase()}`, {
            file
        })
        .then(() => {
            self.$toast().success(self.$t(dataType) + ' ' + self.$t("update ok"));
        })
}
