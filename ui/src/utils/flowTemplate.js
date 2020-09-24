import action from "../models/action";
import permission from "../models/permission";

export function canSaveFlowTemplate(isEdit, user, file, dataType) {
    const typedPermission = permission[dataType.toUpperCase()]
    return (
        isEdit && user &&
        user.isAllowed(typedPermission, action.UPDATE, file.namespace)
    ) || (
        !isEdit && user &&
        user.isAllowed(typedPermission, action.CREATE, file.namespace)
    );
}

export function saveFlowTemplate(self, file, dataType) {
    return self.$store
        .dispatch(`${dataType}/save${dataType.capitalize()}`, { [dataType]: file })
        .then((response) => {
            self.$toast().saved(response.id);
        })
}
