import action from "../models/action"
import resource from "../models/resource"
import {Me} from "../override/stores/auth"

type FlowTemplateItem = {
    namespace?: string
}

export function canSaveFlowTemplate(isEdit: boolean, user: Me | undefined, item: FlowTemplateItem, dataType: string) {
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

export function saveFlowTemplate(self: {
    templateStore: {
    saveTemplate: (data: {template: string}) => Promise<{id: string}>
},
    flowStore: {
    saveFlow: (data: {flow: string}) => Promise<{id: string}>
},
    $toast: () => {
    saved: (name: string) => void
},
}, file: string, dataType: string) {
    return (dataType === "template" ? self.templateStore.saveTemplate({template: file}) : self.flowStore.saveFlow({flow: file}))
        .then((response: { id: string }) => {
            self.$toast().saved(response.id)

            return response
        })
}
