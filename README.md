# SchedulerService

Microsservico responsavel por orquestrar a avaliacao periodica de regras de monitoramento e solicitar a atualizacao de ativos via RabbitMQ. Utiliza Quartz Scheduler para disparar dois jobs em intervalos configurados.

## Tecnologias

- Java 25
- Spring Boot 3.5
- Quartz Scheduler (in-memory)
- Spring AMQP (RabbitMQ)
- Spring Data JPA / Hibernate
- Spring Retry + AOP
- MySQL 8.4
- Lombok
- jqwik (property-based testing)
- Docker Compose

## Arquitetura

O projeto segue Clean Architecture com as seguintes camadas:

```
domain          - Entidades (Asset, Rule, RuleGroup, Alert, User), eventos, ports (interfaces in/out)
                  strategy - Estrategias de comparacao (GREATER_THAN, LESS_THAN, EQUAL, etc.)
application     - Use cases (EvaluateRulesUseCase, RequestAssetUpdateUseCase)
adapters        - Jobs Quartz (scheduler), publishers RabbitMQ (messaging), adaptadores JPA (persistence)
infrastructure  - Configuracoes Spring (Quartz, RabbitMQ, Retry)
```

### Jobs Quartz

O servico registra dois jobs que executam em paralelo com o mesmo intervalo (`SCHEDULER_INTERVAL_MS`):

| Job                      | Classe                    | Descricao                                                        |
|--------------------------|---------------------------|------------------------------------------------------------------|
| `assetUpdateRequestJob`  | `AssetUpdateRequestJob`   | Publica evento `UPDATE_ASSETS` com os tickers das regras ativas  |
| `ruleEvaluationJob`      | `RuleEvaluationJob`       | Avalia regras e grupos, publica `ALERT_TRIGGERED` se disparadas  |

O `ruleEvaluationJob` inicia com um delay de `ASSET_UPDATE_DELAY_MS` apos o startup, garantindo que os ativos sejam atualizados antes da primeira avaliacao.

### Fluxo principal

```
Quartz (a cada SCHEDULER_INTERVAL_MS)
    -> AssetUpdateRequestJob
        -> RequestAssetUpdateUseCaseImpl
            -> RuleRepository + RuleGroupRepository (extrai tickers ativos)
            -> RabbitMqAssetUpdateEventPublisher
                -> invest.assets.exchange (routing key: asset.update)
                    -> invest.assets.update.queue  [consumido pelo asset-update-service]

Quartz (a cada SCHEDULER_INTERVAL_MS, com delay inicial de ASSET_UPDATE_DELAY_MS)
    -> RuleEvaluationJob
        -> EvaluateRulesUseCaseImpl
            -> RuleRepository + RuleGroupRepository (regras ativas)
            -> AssetRepository (precos atuais)
            -> ComparisonStrategyFactory (avalia condicoes)
            -> AlertRepository (persiste alerta PENDING)
            -> RabbitMqEventPublisher
                -> invest.alerts.exchange (routing key: alert.triggered)
                    -> invest.alerts.notification.queue  [consumido pelo dispatcher-service]
```

### Topologia RabbitMQ

O servico declara e publica em dois pares de exchange/fila:

**Atualizacao de ativos:**

| Recurso          | Nome                          |
|------------------|-------------------------------|
| Exchange         | `invest.assets.exchange`      |
| Routing key      | `asset.update`                |
| Fila             | `invest.assets.update.queue`  |
| DLX              | `invest.assets.dlx.exchange`  |
| DLQ              | `invest.assets.update.dlq`    |

**Notificacao de alertas:**

| Recurso          | Nome                              |
|------------------|-----------------------------------|
| Exchange         | `invest.alerts.exchange`          |
| Routing key      | `alert.triggered`                 |
| Fila             | `invest.alerts.notification.queue`|
| DLX              | `invest.alerts.dlx.exchange`      |
| DLQ              | `invest.alerts.notification.dlq`  |

### Estrategias de comparacao

As regras sao avaliadas via Strategy Pattern. Operadores suportados:

| Operador              | Descricao                  |
|-----------------------|----------------------------|
| `GREATER_THAN`        | Valor atual > alvo         |
| `GREATER_THAN_OR_EQUAL` | Valor atual >= alvo      |
| `LESS_THAN`           | Valor atual < alvo         |
| `LESS_THAN_OR_EQUAL`  | Valor atual <= alvo        |
| `EQUAL`               | Valor atual == alvo        |

Campos monitoraveis: `CURRENT_PRICE`, `DIVIDEND_YIELD`, `P_VP`.

## Pre-requisitos

- Java 25+
- Docker e Docker Compose
- Maven 3.9+ (ou use o wrapper `./mvnw`)

## Como rodar (standalone)

Este servico pode ser executado de forma independente, sem depender dos demais servicos do projeto.

### 1. Subir as dependencias

```bash
docker compose up -d
```

Isso inicia:
- **MySQL 8.4** na porta `3307`, com schema e dados de seed carregados automaticamente via `docker/mysql/init/`
- **RabbitMQ** nas portas `5672` (AMQP) e `15672` (Management UI: http://localhost:15672)

### 2. Iniciar a aplicacao

```bash
./mvnw spring-boot:run
```

A aplicacao conecta ao MySQL em `localhost:3307` e ao RabbitMQ em `localhost:5672` por padrao. Os jobs Quartz iniciam automaticamente apos o startup.

## Banco de dados

Os scripts de inicializacao em `docker/mysql/init/` sao executados automaticamente na primeira vez que o container MySQL e criado:

| Arquivo                    | Descricao                               |
|----------------------------|-----------------------------------------|
| `00-schema.sql`            | DDL completo (tabelas, indices, FKs)    |
| `01-seed-demo-user.sql`    | Usuario demo para desenvolvimento local |
| `02-seed-assets.sql`       | Ativos FII para testes                  |

> Para recriar o banco do zero, remova o volume: `docker compose down -v && docker compose up -d`

## Variaveis de ambiente

| Variavel                        | Padrao              | Descricao                                                    |
|---------------------------------|---------------------|--------------------------------------------------------------|
| `SPRING_RABBITMQ_HOST`          | `localhost`         | Host do RabbitMQ                                             |
| `SPRING_RABBITMQ_PORT`          | `5672`              | Porta AMQP do RabbitMQ                                       |
| `SPRING_RABBITMQ_USERNAME`      | `guest`             | Usuario do RabbitMQ                                          |
| `SPRING_RABBITMQ_PASSWORD`      | `guest`             | Senha do RabbitMQ                                            |
| `MYSQL_HOST`                    | `localhost`         | Host do MySQL                                                |
| `MYSQL_PORT`                    | `3306`              | Porta do MySQL                                               |
| `MYSQL_DATABASE`                | `investalert`       | Nome do banco de dados                                       |
| `MYSQL_USERNAME`                | `root`              | Usuario do banco                                             |
| `MYSQL_PASSWORD`                | `root`              | Senha do banco                                               |
| `MYSQL_POOL_MIN_IDLE`           | `2`                 | Minimo de conexoes ociosas no pool                           |
| `MYSQL_POOL_MAX_SIZE`           | `5`                 | Maximo de conexoes no pool                                   |
| `MYSQL_POOL_IDLE_TIMEOUT`       | `30000`             | Timeout de conexao ociosa (ms)                               |
| `MYSQL_POOL_CONNECTION_TIMEOUT` | `20000`             | Timeout de conexao (ms)                                      |
| `SCHEDULER_INTERVAL_MS`         | `300000`            | Intervalo de execucao dos jobs (ms)                          |
| `ASSET_UPDATE_DELAY_MS`         | `30000`             | Delay inicial do job de avaliacao apos o startup (ms)        |
| `QUARTZ_THREAD_COUNT`           | `2`                 | Numero de threads do pool Quartz                             |
| `TZ`                            | `America/Sao_Paulo` | Timezone da aplicacao                                        |

## Testes

```bash
./mvnw test
```

A suite inclui:

- Testes unitarios para logica de dominio e use cases
- Property-based tests com jqwik
- H2 em memoria para testes de unidade que envolvem persistencia

## Build da imagem Docker

```bash
docker build -t scheduler-service .
```

O `Dockerfile` usa multi-stage build: compila com `eclipse-temurin:25-jdk` e gera a imagem final com `eclipse-temurin:25-jre`.
