import permission from "../../models/permission";
import action from "../../models/action";
import {useAuthStore} from "../stores/auth";

export function canCreate(kind: string) {
    const authStore = useAuthStore();

    switch (kind) {
        case "flow": return authStore.user?.hasAnyAction(permission.FLOW, action.CREATE);
        case "dashboard": return authStore.user?.hasAnyAction(permission.DASHBOARD, action.CREATE);
    }
}
