import action from "../models/action";
import permission from "../models/permission";

export function canSaveFlowTemplate(isEdit, user, item, dataType) {
    if (item === undefined) {
        return  true;
    }

    const typedPermission = permission[dataType.toUpperCase()]

    return (
        isEdit && user &&
        user.hasAnyActionOnAnyNamespace(typedPermission, action.UPDATE)
    ) || (
        !isEdit && user &&
        user.hasAnyActionOnAnyNamespace(typedPermission, action.CREATE)
    );
}

export function saveFlowTemplate(self, file, dataType) {
    return self.$store
        .dispatch(`${dataType}/save${dataType.capitalize()}`, {[dataType]: file})
        .then((response) => {
            self.$toast().saved(response.id);

            return response
        })
}
