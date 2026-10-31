import action from "../models/action"
import resource from "../models/resource"

type UserPermissions = {
    isAllowed: (permission: string, action: string, namespace?: string) => boolean
} | undefined

export function canSaveFlowTemplate(isEdit: boolean, user: UserPermissions, item: {namespace?: string} | undefined, dataType: string) {
    if (item === undefined) {
        return  true
    }

    const typedResource = resource[dataType.toUpperCase() as keyof typeof resource]

    return (
        isEdit && user &&
        user.isAllowed(typedResource, action.UPDATE, item.namespace)
    ) || (
        !isEdit && user &&
        user.isAllowed(typedResource, action.CREATE, item.namespace)
    )
}

interface FlowTemplate {
    templateStore: {saveTemplate: (payload: {template: string}) => Promise<{id: string}>}
    flowStore: {saveFlow: (payload: {flow: string}) => Promise<{id: string}>}
    $toast: () => {saved: (id: string) => void}
}

export function saveFlowTemplate(self: FlowTemplate, file: string, dataType: string) {
    return (dataType === "template" ? self.templateStore.saveTemplate({template: file}) : self.flowStore.saveFlow({flow: file}))
        .then((response: { id: string }) => {
            self.$toast().saved(response.id)

            return response
        })
}
