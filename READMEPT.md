Com certeza! Aqui está a tradução do texto para o português do Brasil.

<p align="center">
<a href="https://www.kestra.io">
<img src="https://kestra.io/banner.png"  alt="Orquestrador de workflows Kestra" />
</a>
</p>

<h1 align="center" style="border-bottom: none">
Plataforma de Orquestração Declarativa e Orientada a Eventos
</h1>

<div align="center">
<a href="https://github.com/kestra-io/kestra/releases"><img src="https://img.shields.io/github/tag-pre/kestra-io/kestra.svg?color=blueviolet" alt="Última Versão" /></a>
<a href="https://github.com/kestra-io/kestra/blob/develop/LICENSE"><img src="https://img.shields.io/github/license/kestra-io/kestra?color=blueviolet" alt="Licença" /></a>
<a href="https://github.com/kestra-io/kestra/stargazers"><img src="https://img.shields.io/github/stars/kestra-io/kestra?color=blueviolet&logo=github" alt="Estrela no Github" /></a> 



<a href="https://kestra.io"><img src="https://img.shields.io/badge/Website-kestra.io-192A4E?color=blueviolet" alt="Plataforma de orquestração e agendamento infinitamente escalável Kestra"></a>
<a href="https://kestra.io/slack"><img src="https://img.shields.io/badge/Slack-Junte-se%20à%20Comunidade-blueviolet?logo=slack" alt="Slack"></a>
</div>

<br />

<p align="center">
<a href="https://x.com/kestra_io"><img height="25" src="https://kestra.io/twitter.svg" alt="X (antigo Twitter)" /></a> &nbsp;
<a href="https://www.linkedin.com/company/kestra/"><img height="25" src="https://kestra.io/linkedin.svg" alt="linkedin" /></a> &nbsp;
<a href="https://www.youtube.com/@kestra-io"><img height="25" src="https://kestra.io/youtube.svg" alt="youtube" /></a> &nbsp;
</p>

<p align="center">
<a href="https://trendshift.io/repositories/2714" target="_blank">
<img src="https://trendshift.io/api/badge/repositories/2714" alt="kestra-io%2Fkestra | Trendshift" width="250" height="55"/>
</a>
<a href="https://www.producthunt.com/posts/kestra?embed=true&utm_source=badge-top-post-badge&utm_medium=badge&utm_souce=badge-kestra" target="_blank"><img src="https://api.producthunt.com/widgets/embed-image/v1/top-post-badge.svg?post_id=612077&theme=light&period=daily&t=1740737506162" alt="Kestra - Plataforma completa de automação e orquestração | Product Hunt" style="width: 250px; height: 54px;" width="250" height="54" /></a>
</p>

<p align="center">
<a href="https://go.kestra.io/video/product-overview" target="_blank">
<img src="https://kestra.io/startvideo.png" alt="Comece em 3 minutos com Kestra" width="640px" />
</a>
</p>
<p align="center" style="color:grey;"><i>Clique na imagem para aprender como começar com Kestra em 3 minutos.</i></p>

🌟 O que é Kestra?
Kestra é uma plataforma de orquestração de código aberto e orientada a eventos que facilita tanto workflows agendados quanto orientados a eventos. Ao trazer as melhores práticas de Infraestrutura como Código para a orquestração de dados, processos e microsserviços, você pode construir workflows confiáveis diretamente da UI com apenas algumas linhas de YAML.

Principais Características:

Tudo como Código e a partir da UI: mantenha workflows como código com a integração de Controle de Versão com Git, mesmo ao construí-los pela UI.

Workflows Agendados e Orientados a Eventos: automatize workflows agendados e em tempo real orientados a eventos através de uma simples definição de trigger (gatilho).

Interface Declarativa em YAML: defina workflows usando uma configuração simples no editor de código embutido.

Rico Ecossistema de Plugins: centenas de plugins integrados para extrair dados de qualquer banco de dados, armazenamento em nuvem ou API, e executar scripts em qualquer linguagem.

UI Intuitiva e Editor de Código: construa e visualize workflows diretamente da UI com destaque de sintaxe, autocompletar e validação de sintaxe em tempo real.

Escalável: projetado para lidar com milhões de workflows, com alta disponibilidade e tolerância a falhas.

Amigável ao Controle de Versão: escreva seus workflows no editor de código embutido e envie-os para seu branch Git preferido diretamente do Kestra, habilitando as melhores práticas com pipelines de CI/CD e sistemas de controle de versão.

Estrutura e Resiliência: domine o caos e traga resiliência aos seus workflows com namespaces, rótulos (labels), subfluxos (subflows), tentativas (retries), tempo limite (timeout), tratamento de erros, entradas (inputs), saídas (outputs) que geram artefatos na UI, variáveis, ramificação condicional, agendamento avançado, gatilhos de eventos, preenchimentos retroativos (backfills), tarefas dinâmicas, tarefas sequenciais e paralelas, e pule tarefas ou gatilhos quando necessário definindo a flag disabled como true.

🧑‍💻 A definição YAML é ajustada automaticamente sempre que você faz alterações em um workflow pela UI ou através de uma chamada de API. Portanto, a lógica de orquestração é sempre gerenciada declarativamente em código, mesmo se você modificar seus workflows de outras maneiras (UI, CI/CD, Terraform, chamadas de API).

<p align="center">
<img src="https://kestra.io/adding-tasks.gif" alt="Adicionando novas tarefas na UI">
</p>

🚀 Início Rápido
Comece Localmente em 5 Minutos
Inicie o Kestra com Docker
Certifique-se de que o Docker está em execução. Em seguida, inicie o Kestra com um único comando:

Bash

docker run --pull=always --rm -it -p 8080:8080 --user=root \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v /tmp:/tmp kestra/kestra:latest server local
Se você está no Windows e usa o PowerShell:

PowerShell

docker run --pull=always --rm -it -p 8080:8080 --user=root `
    -v "/var/run/docker.sock:/var/run/docker.sock" `
    -v "C:/Temp:/tmp" kestra/kestra:latest server local
Se você está no Windows e usa o Prompt de Comando (CMD):

DOS

docker run --pull=always --rm -it -p 8080:8080 --user=root ^
    -v "/var/run/docker.sock:/var/run/docker.sock" ^
    -v "C:/Temp:/tmp" kestra/kestra:latest server local
Se você está no Windows e usa o WSL (ambiente baseado em Linux no Windows):

Bash

docker run --pull=always --rm -it -p 8080:8080 --user=root \
    -v "/var/run/docker.sock:/var/run/docker.sock" \
    -v "C:/Temp:/tmp" kestra/kestra:latest server local
Confira nosso Guia de Instalação para outras opções de implantação (Docker Compose, Podman, Kubernetes, AWS, GCP, Azure e mais).

Acesse a UI do Kestra em http://localhost:8080 e comece a construir seu primeiro flow!

Seu Primeiro Flow "Hello World"
Crie um novo flow com o seguinte conteúdo:

YAML

id: hello_world
namespace: dev

tasks:
  - id: say_hello
    type: io.kestra.plugin.core.log.Log
    message: "Hello, World!"
Execute o flow e veja a saída na UI!

🧩 Ecossistema de Plugins
A funcionalidade do Kestra é estendida através de um rico ecossistema de plugins que permite que você execute tarefas em qualquer lugar e programe em qualquer linguagem, incluindo Python, Node.js, R, Go, Shell e mais. Veja como os plugins do Kestra aprimoram seus workflows:

Execute em Qualquer Lugar:

Execução Local ou Remota: Execute tarefas na sua máquina local, em servidores remotos via SSH, ou dimensione para contêineres serverless usando Task Runners.

Suporte a Docker e Kubernetes: Execute contêineres Docker de forma transparente em seus workflows ou inicie jobs do Kubernetes para lidar com cargas de trabalho computacionalmente intensivas.

Programe em Qualquer Linguagem:

Suporte a Scripts: Escreva scripts em sua linguagem de programação preferida. O Kestra suporta Python, Node.js, R, Go, Shell e outras, permitindo que você integre bases de código e padrões de implantação existentes.

Automação Flexível: Execute comandos shell, rode consultas SQL em vários bancos de dados e faça requisições HTTP para interagir com APIs.

Processamento em Tempo Real e Orientado a Eventos:

Gatilhos em Tempo Real: Reaja a eventos de sistemas externos em tempo real, como a chegada de arquivos, novas mensagens em barramentos de mensagens (Kafka, Redis, Pulsar, AMQP, MQTT, NATS, AWS SQS, Google Pub/Sub, Azure Event Hubs) e mais.

Eventos Personalizados: Defina eventos personalizados para acionar flows com base em condições específicas ou sinais externos, permitindo workflows altamente responsivos.

Integrações com a Nuvem:

AWS, Google Cloud, Azure: Integre com uma variedade de serviços em nuvem para interagir com soluções de armazenamento, sistemas de mensagens, recursos de computação e muito mais.

Processamento de Big Data: Execute tarefas de processamento de big data usando ferramentas como Apache Spark ou interaja com plataformas de análise como Google BigQuery.

Monitoramento e Notificações:

Mantenha-se Informado: Envie mensagens para canais do Slack, notificações por e-mail ou acione alertas no PagerDuty para manter sua equipe atualizada sobre o status dos workflows.

O ecossistema de plugins do Kestra está em contínua expansão, permitindo que você adapte a plataforma às suas necessidades específicas. Seja orquestrando pipelines de dados complexos, automatizando scripts em múltiplos ambientes ou integrando com serviços em nuvem, provavelmente existe um plugin para ajudar. E se não houver, você sempre pode construir seus próprios plugins para estender as capacidades do Kestra.

🧑‍💻 Nota: Isso é apenas uma amostra do que os plugins do Kestra podem fazer. Explore a lista completa em nossa Página de Plugins.

📚 Conceitos-Chave
Flows: a unidade central no Kestra, representando um workflow composto por tarefas.

Tasks (Tarefas): unidades de trabalho individuais, como executar um script, mover dados ou chamar uma API.

Namespaces: agrupamento lógico de flows para organização e isolamento.

Triggers (Gatilhos): agendamentos ou eventos que iniciam a execução dos flows.

Inputs (Entradas) e Variáveis: parâmetros e dados dinâmicos passados para flows e tarefas.

🎨 Construa Workflows Visualmente
Kestra fornece uma UI intuitiva que permite construir e visualizar interativamente seus workflows:

Interface de Arrastar e Soltar: adicione e reorganize tarefas no Editor de Topologia.

Validação em Tempo Real: feedback instantâneo sobre a sintaxe e estrutura do seu workflow para detectar erros antecipadamente.

Autocompletar: sugestões inteligentes enquanto você digita para escrever o código do flow rapidamente e sem erros de sintaxe.

Visualização da Topologia ao Vivo: veja seu workflow como um Grafo Acíclico Dirigido (DAG) que se atualiza em tempo real.

🔧 Extensível e Amigável para Desenvolvedores
Desenvolvimento de Plugins
Crie plugins personalizados para estender as capacidades do Kestra. Confira nosso Guia do Desenvolvedor de Plugins para começar.

Infraestrutura como Código
Controle de Versão: armazene seus flows em repositórios Git.

Integração CI/CD: automatize a implantação de flows usando pipelines de CI/CD.

Provedor Terraform: gerencie os recursos do Kestra com o provedor Terraform oficial.

🌐 Junte-se à Comunidade
Mantenha-se conectado e obtenha suporte:

Slack: Junte-se à nossa comunidade no Slack para fazer perguntas e compartilhar ideias.

LinkedIn: Siga-nos no LinkedIn — ao lado do Slack e do GitHub, este é nosso principal canal para compartilhar atualizações e anúncios de produtos.

YouTube: Inscreva-se em nosso canal do YouTube para conteúdo de vídeo educacional. Publicamos novos vídeos toda semana!

X: Siga-nos no X se você ainda estiver ativo por lá.

🤝 Contribuindo
Agradecemos contribuições de todos os tipos!

Relate Problemas (Issues): Encontrou um bug ou tem uma sugestão de funcionalidade? Abra uma issue no GitHub.

Contribua com Código: Confira nosso Guia do Contribuidor para diretrizes iniciais e explore nossas boas primeiras issues para tarefas amigáveis para iniciantes.

Desenvolva Plugins: Crie e compartilhe plugins usando nosso Guia do Desenvolvedor de Plugins.

Contribua com nossa Documentação: Contribua com edições ou atualizações para manter nossa documentação de primeira linha.

📄 Licença
Kestra é licenciado sob a Licença Apache 2.0 © Kestra Technologies.

⭐️ Mantenha-se Atualizado
Dê uma estrela ao nosso repositório para se manter informado sobre as últimas funcionalidades e atualizações!

Obrigado por considerar o Kestra para suas necessidades de orquestração de workflows. Mal podemos esperar para ver o que você vai construir!