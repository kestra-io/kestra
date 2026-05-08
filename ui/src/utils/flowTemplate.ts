import action from "../models/action";
import resource from "../models/resource";

export function canSaveFlowTemplate(isEdit: boolean, user: any, item: any, dataType: string) {
    if (item === undefined) {
        return  true;
    }

    const typedResource = resource[dataType.toUpperCase() as keyof typeof resource]

    return (
        isEdit && user &&
        user.isAllowed(typedResource, action.UPDATE, item.namespace)
    ) || (
        !isEdit && user &&
        user.isAllowed(typedResource, action.CREATE, item.namespace)
    );
}

export function saveFlowTemplate(self: {
    templateStore: any,
    flowStore: any,
    $toast: () => any,
}, file: string, dataType: string) {
    return (dataType === "template" ? self.templateStore.saveTemplate({template: file}) : self.flowStore.saveFlow({flow: file}))
        .then((response: { id: string }) => {
            self.$toast().saved(response.id);

            return response
        })
}
