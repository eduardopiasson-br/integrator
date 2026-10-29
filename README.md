# activation-service

Serviço de ativação de produtos de clientes: registra solicitações, consulta o status e integra com um provedor externo de ativação.

## Como executar

Requisitos: JDK 21. O Maven é baixado automaticamente pelo wrapper.

```bash
./mvnw verify      # build + testes
./mvnw spring-boot:run
```

Os testes de integração usam **Testcontainers** (PostgreSQL real), portanto é necessário Docker em execução.

## API

| Método | Caminho | Descrição | Respostas |
|---|---|---|---|
| `POST` | `/api/v1/requests` | Cria uma solicitação de ativação | `201 Created` + header `Location`; `400` dados inválidos |
| `GET` | `/api/v1/requests/{id}` | Consulta uma solicitação e seu status | `200`; `404` inexistente; `400` id fora do formato UUID |

```bash
curl -i -X POST localhost:8080/api/v1/requests \
  -H 'Content-Type: application/json' \
  -d '{"externalId":"EXT-12345","customerId":"CUST-10001","productCode":"INTERNET-500"}'
```

### Regras de validação

| Campo | Regra |
|---|---|
| `externalId`, `customerId`, `productCode` | obrigatórios, 1 a 64 caracteres, apenas `A-Z a-z 0-9 - _` (identificadores de outros sistemas: sem espaços ou acentos) |

Datas sempre em UTC, ISO-8601 (`2026-10-26T14:03:11.123Z`).

### Erros

Toda resposta de erro segue **Problem Details (RFC 9457)**, `Content-Type: application/problem+json`,
com um `traceId` que identifica a requisição nos logs. Nenhuma resposta expõe stack trace ou mensagens
internas.

```json
{
  "type": "urn:activation-service:problem:validation-error",
  "title": "Dados inválidos",
  "status": 400,
  "detail": "Um ou mais campos não passaram na validação.",
  "instance": "/api/v1/requests",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "errors": [ { "field": "customerId", "message": "não deve estar em branco" } ]
}
```

| `type` (sufixo) | HTTP | Quando |
|---|---|---|
| `validation-error` | 400 | campos inválidos (`errors[]` lista campo e mensagem) |
| `malformed-request` | 400 | JSON inválido ou parâmetro com tipo errado |
| `not-found` | 404 | solicitação inexistente |
| `internal-error` | 500 | erro inesperado; o detalhe completo fica no log, localizável pelo `traceId` |

**Correlação**: o cliente pode enviar `X-Correlation-Id`; se não enviar (ou enviar um valor inseguro),
o serviço gera um. O valor volta no header da resposta, é o `traceId` dos erros e aparece em todas as
linhas de log da requisição (`[correlationId]`).

## Decisões técnicas

### API

- **DTOs separados da entidade** (`CreateActivationRequestRequest`, `ActivationRequestResponse`, ambos `record`):
  a entidade JPA nunca é serializada, então mudanças internas (campos de controle, versão) não vazam
  para o contrato e o cliente não consegue enviar campos que não deveria (ex.: `status`).
- **Validação na borda** com Bean Validation; o service recebe um `CreateActivationRequestCommand` já válido.
- **`201 Created` com `Location`** apontando para o recurso criado, conforme a semântica HTTP.
- **Camadas**: `api` (HTTP) -> `application` (casos de uso, transação) -> `domain` (entidade, regras, repositório).
  A dependência é sempre nessa direção.
- **Tratamento de erros centralizado** em `ApiExceptionHandler`, que estende `ResponseEntityExceptionHandler`
  para herdar o mapeamento padrão do Spring MVC (405, 415, rota inexistente...) e apenas personaliza o que
  importa para a API. Exceções de domínio/aplicação não conhecem HTTP; o mapeamento para status fica na
  camada `api`. Erros 5xx são logados com stack trace e `traceId`; 4xx não geram log de erro.
- **Locale fixo (`pt_BR`)**: mensagens de validação não variam com o `Accept-Language` do cliente.


### Domínio e persistência

- **Estados**: `PENDING → PROCESSING → COMPLETED | FAILED`. As transições vivem na entidade
  (`startProcessing()`, `complete()`, `fail()`); não existe `setStatus`. Uma transição inválida
  (ex.: `COMPLETED → PROCESSING`) lança `InvalidStatusTransitionException`. A regra fica em um
  `switch` exaustivo no enum, então um estado novo obriga a decidir suas transições em tempo de compilação.
- **Lock otimista** com `@Version`: duas atualizações concorrentes da mesma solicitação não se
  sobrescrevem; a segunda recebe `OptimisticLockException` e precisa reler o registro.
- **Id gerado pela aplicação** (UUID): conhecido antes do `INSERT`, o que permite usá-lo como chave de
  idempotência na chamada ao provedor. Como a entidade tem `@Version`, o Spring Data reconhece o
  objeto como novo (`version == null`) e faz `persist` direto, sem `SELECT` prévio.
- **Schema versionado com Flyway** (`src/main/resources/db/migration`); o Hibernate roda com
  `ddl-auto: validate`, apenas conferindo que entidade e tabela batem.
- **Unicidade de `externalId` garantida por constraint no banco** (`uk_activation_request_external_id`).
  É a única barreira que funciona para requisições simultâneas; a semântica da resposta HTTP para
  duplicatas é tratada na camada de API (ver seção de duplicidade).
- **Datas em UTC** (`Instant` + `TIMESTAMPTZ`, `hibernate.jdbc.time_zone=UTC`).

_(as demais seções serão preenchidas conforme a implementação avançar)_
