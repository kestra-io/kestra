import {LOG_LEVELS} from "@kestra-io/design-system"
import type {LevelKey} from "../../utils/logs"
import {storageKeys} from "../../utils/constants"

export const toLevelKey = (value: string | null): LevelKey => LOG_LEVELS.find((level) => level === value) ?? "INFO"

export const defaultTaskRunLogLevel = (): LevelKey => toLevelKey(localStorage.getItem(storageKeys.DEFAULT_LOG_LEVEL))
