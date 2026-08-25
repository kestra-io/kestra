import {storageKeys, taskEditDefaultModes} from "../../../utils/constants"

export function opensInModalByDefault(): boolean {
    const mode = localStorage.getItem(storageKeys.TASK_EDIT_DEFAULT_MODE) || taskEditDefaultModes.MODAL
    return mode !== taskEditDefaultModes.TAB
}
