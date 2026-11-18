import type {Notification} from "@kestra-io/kestra-sdk"

/** Runs before the notifications panel navigates to a notification's target; OSS has a single tenant, so nothing to prepare. */
export async function beforeNotificationNavigation(_notification: Notification) {}
