import {$} from "@hey-api/openapi-ts";
import type {KestraSdkPlugin} from "./types";


export const handler: KestraSdkPlugin["Handler"] = ({plugin}) => {
  const useRouteSymbol = plugin.symbol(
    "useRoute", 
    {
        external: "vue-router"
    });   
  
  const addTenantToParametersSymbol = plugin.symbol("addTenantToParameters",{
        getFilePath: () => "sdk/ks-shared",
  });

  const functionNode = $.func().generic("TParams")
    .params(
      $.param("parameters").type($.type("TParams"))
    ).returns($.type.and($.type("TParams"), $.type.object().prop("tenant", (p) => p.type("string"))))
    .do(
      // const tenant = useRouter().params.tenant
      $.const("tenant").assign(
        $(useRouteSymbol).call().attr("params").attr("tenant").optional().as($.type("string"))
      ),
      $.return($.object()
        .prop("tenant", "tenant")
        .spread($.id("parameters"))
      )
    )

  const exportedFunctionNode = $.const(addTenantToParametersSymbol).export().assign(functionNode);
  plugin.node(exportedFunctionNode);

  const operationsDict: Record<string, {symbol:ReturnType<typeof plugin.symbol>, methodName: string}[]> = {}
  
  plugin.forEach(
    "operation",
    ({operation}) => {
        // on each operation, create a method that executes the operation from the sdk
        const methodName = plugin.config.methodNameBuilder?.(operation);
        if (!methodName) {
            return;
        }

        const pathParams = operation.parameters?.path || {};

        const sym = plugin.querySymbol({
          category: "sdk",
          resource: "operation",
          resourceId: operation.id,
        })

        if(!sym) {
            return;
        }

        const originalOperationSymbol = $(sym);

        const funcSymbol = plugin.symbol(methodName, {
            getFilePath: () => `sdk/ks-${operation.tags?.[0] ?? "default"}`,
        })

        if (!operationsDict[operation.tags?.[0] ?? "default"]) {
            operationsDict[operation.tags?.[0] ?? "default"] = [];
        }
        operationsDict[operation.tags?.[0] ?? "default"].push({symbol:funcSymbol, methodName});

        if(!pathParams || !("tenant" in pathParams)) {
            // if there is no path parameter named "tenant", 
            // we export this method as is
            plugin.node(
                $.const(funcSymbol)
                .assign(originalOperationSymbol)
                .export()
            );
            return;
        }

        const optionsId = "options"

        const isTenantOnlyRequiredParam = Object.values(pathParams).filter(p => p.name !== "tenant" && p.required).length === 0;

        const paramId = "parameters"
        const functionNode = $.func()
            .params(
                $.param(paramId)
                    .required(!isTenantOnlyRequiredParam)
                    .type($.type.and(
                        $.type("Omit").generics($.type("Parameters")
                            .generic($.type.query(originalOperationSymbol))
                            .idx(0), $.type.literal("tenant"))
                        ,
                            $.type.object().prop("tenant", (p) => p.type("string").optional())
                        )
                    )
                    ,
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

        const exportedFunctionNode = $.const(funcSymbol).export().assign(functionNode)
            .doc(operation.summary);

        plugin.node(exportedFunctionNode);
    },
    {
      order: "declarations",
    },
  );
};