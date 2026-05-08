import resource from "../../models/resource";
import action from "../../models/action";
import {useAuthStore} from "../stores/auth";

export function canCreate(kind: string) {
    const authStore = useAuthStore();

    switch (kind) {
        case "flow": return authStore.user?.hasAnyAction(resource.FLOW, action.CREATE);
        case "dashboard": return authStore.user?.hasAnyAction(resource.DASHBOARD, action.CREATE);
    }
}
