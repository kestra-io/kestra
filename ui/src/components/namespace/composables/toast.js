import {ElNotification, ElMessageBox, ElTable, ElTableColumn} from "element-plus";
import {h} from "vue";

export function useToast() {
  const _wrap = function (message) {
    if (Array.isArray(message) && message.length > 0) {
      return h(
        ElTable,
        {
          stripe: true,
          tableLayout: "auto",
          fixed: true,
          data: message,
          class: ["mt-2"],
          size: "small",
        },
        [h(ElTableColumn, {label: "Message", formatter: (row) => h("span", {innerHTML: row.message})})]
      );
    } else {
      return h("span", {innerHTML: message});
    }
  };

  const confirm = function (message, callback) {
    ElMessageBox.confirm(
      _wrap(message || "Confirmation"),
      "Confirmation",
      {
        type: "warning",
      }
    )
      .then(() => {
        callback();
      });
  };

  const saved = function (name, title, options) {
    ElNotification.closeAll();
    const message = options?.multiple ? `Multiple items saved: ${name}` : `Item saved: ${name}`;
    ElNotification({
      ...{
        title: title || "Saved",
        message: _wrap(message),
        position: "bottom-right",
        type: "success",
      },
      ...(options || {})
    });
  };

  const deleted = function (name, title, options) {
    ElNotification({
      ...{
        title: title || "Deleted",
        message: _wrap(`Deleted confirmation: ${name}`),
        position: "bottom-right",
        type: "success",
      },
      ...(options || {})
    });
  };

  const success = function (message, title, options) {
    ElNotification({
      ...{
        title: title || "Success",
        message: _wrap(message),
        position: "bottom-right",
        type: "success",
      },
      ...(options || {})
    });
  };

  const warning = function (message, title, options) {
    ElNotification({
      ...{
        title: title || "Warning",
        message: _wrap(message),
        position: "bottom-right",
        type: "warning",
      },
      ...(options || {})
    });
  };

  const error = function (message, title, options) {
    ElNotification({
      ...{
        title: title || "Error",
        message: _wrap(message),
        position: "bottom-right",
        type: "error",
        duration: 0,
        customClass: "large"
      },
      ...(options || {})
    });
  };

  return {
    confirm,
    saved,
    deleted,
    success,
    warning,
    error
  };
}