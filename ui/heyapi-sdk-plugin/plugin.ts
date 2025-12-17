import {$} from "@hey-api/openapi-ts";
import type {KestraSdkPlugin} from "./types";

export const handler: KestraSdkPlugin["Handler"] = ({plugin}) => {
  const pluginSdk = plugin.getPluginOrThrow("@hey-api/sdk");

  const addTenantToParametersSymbol = plugin.symbol("addTenantToParameters");
  const useRouterSymbol = plugin.symbol("useRouter");
  
  useRouterSymbol.setImportKind("named")

  addTenantToParametersSymbol.setNode($.func("addTenantToParameters").generic("TParams")
    .params(
      $.param("parameters").type($.type("TParams"))
    ).returns($.type.and($.type("TParams"), $.type.object().prop("tenant", (p) => p.type("string"))))
    .do(
        // const tenant = useRouter().params.tenant
      $.return($.object()
        .spread($.id("parameters"))
        .prop("tenant", $.literal("main"))
      )
    ))

  plugin.addNode(addTenantToParametersSymbol.node ?? null);
  
  plugin.forEach(
    "operation",
    ({operation}) => {
        // on each operation, create a method that executes the operation from the sdk
        const sdkMethodName = pluginSdk.config.methodNameBuilder?.(operation);
        if (!sdkMethodName) {
            return;
        }

        const methodName = `ksApi${sdkMethodName.charAt(0).toUpperCase()}${sdkMethodName.slice(1)}`;

        const pathParams = operation.parameters?.path || {};

        const originalOperationSymbol = $(plugin.querySymbol({
          category: "sdk",
          resource: "operation",
          resourceId: operation.id,
        }));

        if(!pathParams || !("tenant" in pathParams)) {
            // if there is no path parameter named "tenant", 
            // we export this method as is
            const exportedFunctionNode = $.const(plugin.symbol(methodName)).export().assign(originalOperationSymbol);
            plugin.addNode(exportedFunctionNode);
            return;
        }

        const paramId = "parameters"
        const optionsId = "options"
        const functionNode = $.func()
            .params(
                $.param(paramId)
                    .type(
                        $.type("Omit").generics(
                            $.type("Parameters")
                                .generic($.type.query(originalOperationSymbol))
                                .idx(0),
                            $.type.literal("tenant")
                        )
                    ),
                $.param(optionsId)
                    .type(
                        $.type("Parameters")
                            .generic($.type.query(originalOperationSymbol))
                            .idx(1)
                        )
                )
            .do(
                $.return(originalOperationSymbol.call(
                    $(addTenantToParametersSymbol).call(paramId),
                    optionsId,
                ))
            )

        const funcSymbol = plugin.symbol(methodName)
        const exportedFunctionNode = $.const(funcSymbol).export().assign(functionNode);

        plugin.addNode(exportedFunctionNode);
    },
    {
      order: "declarations",
    },
  );
};