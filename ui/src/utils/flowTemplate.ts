import action from "../models/action";
import permission from "../models/permission";

export function canSaveFlowTemplate(isEdit: boolean, user: any, item: any, dataType: string) {
    if (item === undefined) {
        return  true;
    }

    const typedPermission = permission[dataType.toUpperCase() as keyof typeof permission]

    return (
        isEdit && user &&
        user.isAllowed(typedPermission, action.UPDATE, item.namespace)
    ) || (
        !isEdit && user &&
        user.isAllowed(typedPermission, action.CREATE, item.namespace)
    );
}

export function saveFlowTemplate(self: {
    $store: any,
    $toast: () => any,
}, file: string, dataType: string) {
    return self.$store
        .dispatch(`${dataType}/save${dataType.capitalize()}`, {[dataType]: file})
        .then((response: { id: string }) => {
            self.$toast().saved(response.id);

            return response
        })
}
