Código de Conduta

Este projeto e todos que participam dele são regidos pelo Código de Conduta Kestra. Ao participar, espera-se que você cumpra este código. Por favor, relate comportamentos inaceitáveis para hello@kestra.io.



Eu Quero Contribuir

Aviso Legal

Ao contribuir para este projeto, você deve concordar que é o autor de 100% do conteúdo, que possui os direitos necessários sobre o conteúdo e que o conteúdo que você contribui pode ser fornecido sob a licença do projeto.



Enviar Issues

Reportando bugs

Relatórios de bugs nos ajudam a tornar o Kestra melhor para todos. Nós fornecemos um modelo pré-configurado para bugs para deixar bem claro quais informações precisamos. Por favor, pesquise em nossos bugs já reportados antes de abrir um novo para garantir que você não está criando um relatório duplicado.



Reportando problemas de segurança

Por favor, não crie uma issue pública no GitHub. Se você encontrou um problema de segurança, por favor, envie-nos um e-mail diretamente para hello@kestra.io em vez de abrir uma issue.



Solicitando novas funcionalidades

Para solicitar novas funcionalidades, por favor, crie uma issue neste projeto. Se você gostaria de sugerir uma nova funcionalidade, pedimos que use nosso modelo de issue. Ele contém algumas perguntas essenciais que nos ajudam a entender o problema que você está tentando resolver e como você acha que sua recomendação o resolverá. Para ver o que já foi proposto pela comunidade, você pode olhar aqui. Fique atento a duplicatas! Se estiver criando uma nova issue, por favor, verifique as issues abertas ou recentemente fechadas. Ter uma única issue com votos é muito mais fácil para nós priorizarmos.



Sua Primeira Contribuição de Código

Requisitos

As seguintes dependências são necessárias para compilar o Kestra localmente:



Java 21+



Node 18+ e npm



Python 3, pip e python venv



Docker \& Docker Compose



Uma IDE (Intellij IDEA, Eclipse ou VS Code)



Graças à comunidade Kestra, se estiver usando o VSCode, você também pode iniciar o desenvolvimento tanto no frontend quanto no backend com um contêiner docker pré-configurado, sem a necessidade de configurar manualmente o ambiente.

Confira o README para instruções de configuração e o Dockerfile associado no repositório para começar.



Para começar a contribuir:



Faça um fork do repositório



Clone o fork na sua estação de trabalho:



Bash



git clone git@github.com:{SEU\_USUARIO}/kestra.git

cd kestra

Desenvolver no backend

O backend é feito com Micronaut.



Abra o repositório clonado em sua IDE favorita. Na maioria das IDEs decentes, o build do Gradle será detectado e todas as dependências serão baixadas. Você também pode compilá-lo a partir de um terminal usando ./gradlew build; o wrapper do Gradle fará o download da versão correta do Gradle a ser usada.



Você pode precisar habilitar os processadores de anotações (annotation processors) do Java, pois estamos usando-os.



No IntelliJ IDEA, clique em Run -> Edit Configurations -> + Add new Configuration para criar uma configuração de execução para iniciar o Kestra.



A classe principal é io.kestra.cli.App do módulo kestra.cli.main.



Passe como argumentos do programa o servidor com o qual você deseja trabalhar, por exemplo, server local iniciará o standalone local. Você também pode usar server standalone e o arquivo Docker Compose docker-compose-ci.yml fornecido para iniciar um servidor standalone com um banco de dados real como backend, que precisaria ser configurado corretamente.



Configure as seguintes variáveis de ambiente:



MICRONAUT\_ENVIRONMENTS: pode ser definido como qualquer string e carregará um arquivo de configuração personalizado em cli/src/main/resources/application-{env}.yml.



KESTRA\_PLUGINS\_PATH: é o caminho onde você salvará os plugins como Jar e eles serão carregados na inicialização.



Veja a captura de tela abaixo para um exemplo:



Se você encontrar o erro JavaScript memory heap out durante a inicialização, configure a variável de ambiente NODE\_OPTIONS com um valor alto.



Exemplo: NODE\_OPTIONS: --max-old-space-size=4096 ou NODE\_OPTIONS: --max-old-space-size=8192



O servidor inicia por padrão na porta 8080 e está acessível em http://localhost:8080



Se você quiser executar todos os testes, precisa do Python e de alguns pacotes instalados em sua máquina. No Ubuntu, você pode instalá-los com:



Bash



sudo apt install python3 pip python3-venv

python3 -m pip install virtualenv

Desenvolver no frontend

O frontend é feito com Vue.js e está localizado na pasta /ui.



Execute npm install



npm run dev iniciará o servidor de desenvolvimento com hot reload.



O servidor inicia por padrão na porta 5173 e está acessível em http://localhost:5173



Você pode executar npm run build para compilar o front-end que será servido pelo backend (sem precisar executar o npm run dev acima).



Agora, você precisa iniciar um servidor backend. Você pode:



iniciar um local server sem um banco de dados usando este arquivo docker-compose já configurado com CORS habilitado:



YAML



services:

&nbsp; kestra:

&nbsp;   image: kestra/kestra:latest

&nbsp;   user: "root"

&nbsp;   command: server local

&nbsp;   environment:

&nbsp;     KESTRA\_CONFIGURATION: |

&nbsp;       micronaut:

&nbsp;         server:

&nbsp;           cors:

&nbsp;             enabled: true

&nbsp;             configurations:

&nbsp;               all:

&nbsp;                 allowedOrigins:

&nbsp;                   - http://localhost:5173

&nbsp;   ports:

&nbsp;     - "8080:8080"

iniciar o Develop backend a partir da sua IDE. Você precisa configurar as restrições de CORS ao usar o servidor npm de desenvolvimento local, alterando a configuração do backend para permitir a origem http://localhost:5173 em cli/src/main/resources/application-override.yml:



YAML



micronaut:

&nbsp; server:

&nbsp;   cors:

&nbsp;     enabled: true

&nbsp;     configurations:

&nbsp;       all:

&nbsp;         allowedOrigins:

&nbsp;           - http://localhost:5173

Compilar e implantar o Kestra localmente

Para fins de teste, você pode usar o Makefile fornecido na raiz do projeto para compilar e implantar o Kestra localmente. Por padrão, o Kestra será instalado em: $HOME/.kestra/current. Defina a variável de ambiente KESTRA\_HOME para substituir o padrão.



Bash



\# compila e instala o Kestra

make install



\# instala os plugins (a instalação de plugins é baseada nos arquivos .plugins ou .plugins.override localizados na raiz do projeto)

make install-plugins



\# inicia o Kestra em modo standalone com Postgres como backend

make start-standalone-postgres

Nota: a instalação local escreve os logs no diretório ~/.kestra/current/logs/.



Desenvolver plugins

Uma documentação completa para o desenvolvimento de plugins pode ser encontrada aqui.



Melhorando a Documentação

A documentação principal está localizada em um repositório separado. A documentação das tarefas está localizada diretamente no código-fonte Java, usando anotações Swagger (Exemplo: para tarefas Bash).

