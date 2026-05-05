export const SECTIONS = {
  TASKS: "TASKS",
  TRIGGERS: "TRIGGERS",
  TASK_RUNNERS: "TASK_RUNNERS",
} as const;

export const EVENTS = {
  EDIT: "edit",
  DELETE: "delete",
  SHOW_DESCRIPTION: "showDescription",
  COLLAPSE: "collapse",
  EXPAND: "expand",
  OPEN_LINK: "openLink",
  ADD_TASK: "addTask",
  SHOW_LOGS: "showLogs",
  MOUSE_OVER: "mouseover",
  MOUSE_LEAVE: "mouseleave",
  ADD_ERROR: "addError",
  EXPAND_DEPENDENCIES: "expandDependencies",
  SHOW_CONDITION: "showCondition",
  RUN_TASK: "runTask",
} as const;

export const CLUSTER_PREFIX = "cluster_";

export const NODE_SIZES = {
  TASK_WIDTH: 184,
  TASK_HEIGHT: 44,
  TRIGGER_WIDTH: 184,
  TRIGGER_HEIGHT: 44,
  DOT_WIDTH: 5,
  DOT_HEIGHT: 5,
  COLLAPSED_CLUSTER_WIDTH: 150,
  COLLAPSED_CLUSTER_HEIGHT: 44,
  TRIGGER_CLUSTER_WIDTH: 350,
  TRIGGER_CLUSTER_HEIGHT: 180,
} as const;
