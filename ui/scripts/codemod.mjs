import {createVueMetamorphCli} from "vue-metamorph";

/**
 * @type {import('vue-metamorph').CodemodPlugin}
 */
const changeStringLiterals = {
  type: "codemod",
  name: "change string literals to hello, world",

  transform({scriptASTs, sfcAST, styleASTs, filename, utils: {traverseScriptAST, traverseTemplateAST, traverseStyleAST}}) {
    // codemod plugins self-report the number of transforms it made
    // this is only used to print the stats in CLI output
    let transformCount = 0;

    // scriptASTs is an array of Program ASTs
    // in a js/ts file, this array will only have one item
    // in a vue file, this array will have one item for each <script> block
    for (const scriptAST of scriptASTs) {
      // traverseScriptAST is an alias for the ast-types 'visit' function
      // see: https://github.com/benjamn/ast-types#ast-traversal
      traverseScriptAST(scriptAST, {
        visitLiteral(path) {
          if (typeof path.node.value === "string") {
            // mutate the node
            // path.node.value = "Hello, world!";
            // transformCount++;
          }

          return this.traverse(path);
        }
      });
    }

    if (sfcAST) {
      // traverseTemplateAST is an alias for the vue-eslint-parser 'AST.traverseNodes' function
      // see: https://github.com/vuejs/vue-eslint-parser/blob/master/src/ast/traverse.ts#L118
      traverseTemplateAST(sfcAST, {
        enterNode(node) {
          if (node.type === "Literal" && typeof node.value === "string") {
            // mutate the node
            // node.value = "Hello, world!";
            // transformCount++;
          }
        },
        leaveNode() {

        },
      });
    }

    if(styleASTs) {
        for(const styleAST of styleASTs) {
            // traverseStyleAST is an alias for the css-tree 'walk' function
            // see:
            traverseStyleAST(styleAST, {
                enter(node) {
                    if (node.type === "decl") {
                        // mutate the node
                        if(node.value.includes()) {
                        transformCount++;
                    }
                },
                leave() {

                },
            });
    }

    return transformCount;
  }
}



const {run, abort} = createVueMetamorphCli({
  silent: true, // suppress vue-metamorph's default output by setting silent:true

  onProgress({
    totalFiles,
    filesProcessed,
    filesRemaining,
    stats,
    aborted,
    done,
    errors,
    manualMigrations,
  }) {
    console.log(`Processed ${filesProcessed}/${totalFiles} files`);
    // called every time a file was transformed
    // also called when vue-metamorph finished processing all files (with done:true)
    // also called when vue-metamorph was aborted via the `abort()` function (with aborted:true)
  },

  // register your CodemodPlugins and/or ManualMigrationPlugins here
  plugins: [changeStringLiterals],
});

run();

// call abort() to gracefully stop the runner
// process.on('SIGINT', abort);