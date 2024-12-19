import {createVueMetamorphCli} from "vue-metamorph";

/**
 * @type {import('vue-metamorph').CodemodPlugin}
 */
const updateCSSVariables = {
  type: "codemod",
  name: "replace bs-variables with ks colors",

  transform({scriptASTs, sfcAST, styleASTs, filename, utils: {traverseScriptAST, traverseTemplateAST}}) {
    // codemod plugins self-report the number of transforms it made
    // this is only used to print the stats in CLI output
    if(styleASTs) {
        for(const styleAST of styleASTs) {
            // traverseStyleAST is an alias for the css-tree 'walk' function
            // see:
            styleAST.walk((node) => {
                    if (node.type === "decl") {
                        // mutate the node
                        if(node.value.includes("var(--bs-")) {
                            node.value = "#FFF"
                        }
                        transformCount++;
                    }
                });
        }
    }

    return transformCount;
  }
}



const {run, abort} = createVueMetamorphCli({
  silent: true, // suppress vue-metamorph's default output by setting silent:true

  onProgress({
    totalFiles,
    filesProcessed,
  }) {
    console.log(`Processed ${filesProcessed}/${totalFiles} files`);
    // called every time a file was transformed
    // also called when vue-metamorph finished processing all files (with done:true)
    // also called when vue-metamorph was aborted via the `abort()` function (with aborted:true)
  },

  // register your CodemodPlugins and/or ManualMigrationPlugins here
  plugins: [updateCSSVariables],
});

run();

// call abort() to gracefully stop the runner
// process.on('SIGINT', abort);