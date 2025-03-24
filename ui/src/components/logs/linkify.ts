// inspired from https://kerkour.com/vuejs-3-router-links-dynamic-vhtml
import type {Router} from "vue-router";

function gotoRoute(event: MouseEvent, route: any, router: Router) {
  const {altKey, ctrlKey, metaKey, shiftKey, button} = event;
  // ignore with control keys
  if (metaKey || altKey || ctrlKey || shiftKey) {
    return;
  }

  // ignore right clicks
  if (button !== undefined && button !== 0) {
    return;
  }
  event.preventDefault();
  router.push(route);
}

/**
 * Tranform router-md elements in an html element use 
 * the vue-router methods instead of natural links 
 * @param element 
 */
export default function linkify(element?: HTMLElement, router?: Router) {
  if (!element || !router) {
    return
  }
  const links = element?.getElementsByTagName("router-md") ?? [];

  Array.from(links).forEach((link: Element) => {
    if (!(link instanceof HTMLElement)) {
      return;
    }

    const execution = link.getAttribute("execution");
    const namespace = link.getAttribute("namespace");
    const flowId = link.getAttribute("flowId");

    const executionRoute = {name: "executions/update", params: {id: execution, namespace: namespace, flowId: flowId}}
    const executionUrl = router.resolve(executionRoute)
    const flowRoute = {name: "flows/update", params: {namespace: namespace, id: flowId}}
    const flowUrl = router.resolve(flowRoute)

    // we create the anchor nodes
    const anchorNodeExection = document.createElement("a");
    anchorNodeExection.href = executionUrl.href;
    anchorNodeExection.textContent = execution;
    anchorNodeExection.onclick = (event: MouseEvent) => {
        gotoRoute(event, executionRoute, router);
    }

    const anchorNodeFlow = document.createElement("a");
    anchorNodeFlow.href = flowUrl.href;
    anchorNodeFlow.textContent = `${namespace}.${flowId}`;
    anchorNodeFlow.onclick = (event: MouseEvent) => {
      gotoRoute(event, flowRoute, router);
    }

    // finally we rebuild the router-md component with the new links
    link.replaceWith(anchorNodeExection);
    anchorNodeExection.after(anchorNodeFlow);
    anchorNodeExection.after(document.createTextNode(" for flow "));
  });
}