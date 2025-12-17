import {$} from "@hey-api/openapi-ts";
import type {KestraSdkPlugin} from "./types";

export const handler: KestraSdkPlugin["Handler"] = ({plugin}) => {
  const useRouteSymbol = plugin.symbol(
    "useRoute", 
    {
        external: "vue-router"
    });   
  
  const addTenantToParametersSymbol = plugin.symbol("addTenantToParameters");
  addTenantToParametersSymbol.setNode($.func("addTenantToParameters").generic("TParams")
    .params(
      $.param("parameters").type($.type("TParams"))
    ).returns($.type.and($.type("TParams"), $.type.object().prop("tenant", (p) => p.type("string"))))
    .do(
      // const tenant = useRouter().params.tenant
      $.const("tenant").assign(
        $(useRouteSymbol).call().attr("params").attr("tenant").optional().as($.type("string"))
      ),
      $.return($.object()
        .spread($.id("parameters"))
        .prop("tenant", "tenant")
      )
    ))

  plugin.addNode(addTenantToParametersSymbol.node ?? null);
  
  plugin.forEach(
    "operation",
    ({operation}) => {
        // on each operation, create a method that executes the operation from the sdk
        const methodName = plugin.config.methodNameBuilder?.(operation);
        if (!methodName) {
            return;
        }

        const pathParams = operation.parameters?.path || {};

        const originalOperationSymbol = $(plugin.querySymbol({
          category: "sdk",
          resource: "operation",
          resourceId: operation.id,
        }));

        if(!pathParams || !("tenant" in pathParams)) {
            // if there is no path parameter named "tenant", 
            // we export this method as is
            plugin.addNode(
                $.const(plugin.symbol(methodName))
                .assign(originalOperationSymbol)
                .export()
            );
            return;
        }

        
        const optionsId = "options"

        if((Object.keys(pathParams).length 
            + Object.keys(operation.parameters?.query || {}).length)
            < 2 && !operation.body) {
            
            // if the only path parameter is "tenant", we can simplify the function
            const functionNode = $.func()
                .params($.param(optionsId)
                    .type(
                        $.type("Parameters")
                            .generic($.type.query(originalOperationSymbol))
                            .idx(1)
                        )
                )
                .do(
                    $.return(originalOperationSymbol.call(
                        $(addTenantToParametersSymbol).call($.object()),
                        optionsId,
                    ))
                )
            const funcSymbol = plugin.symbol(methodName)
            const exportedFunctionNode = $.const(funcSymbol).export().assign(functionNode);

            plugin.addNode(exportedFunctionNode);
            return;
        }

        const isTenantOnlyRequiredParam = Object.values(pathParams).filter(p => p.name !== "tenant" && p.required).length === 0;

        const paramId = "parameters"
        const functionNode = $.func()
            .params(
                $.param(paramId)
                    .required(!isTenantOnlyRequiredParam)
                    .type(
                        $.type("Omit").generics(
                            $.type("Parameters")
                                .generic($.type.query(originalOperationSymbol))
                                .idx(0),
                            $.type.literal("tenant")
                        )
                    ),
                $.param(optionsId)
                    .required(false)
                    .type(
                        $.type("Parameters")
                            .generic($.type.query(originalOperationSymbol))
                            .idx(1)
                        )
                )
            .do(
                isTenantOnlyRequiredParam ?
                    $.return(originalOperationSymbol.call(
                        $(addTenantToParametersSymbol).call($(paramId)),
                        optionsId,
                    ))
                : $.return(originalOperationSymbol.call(
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