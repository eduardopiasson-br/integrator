# activation-service

Serviço de ativação de produtos de clientes: registra solicitações, consulta o status e integra com um provedor externo de ativação.

## Como executar

Requisitos: JDK 21. O Maven é baixado automaticamente pelo wrapper.

```bash
./mvnw verify      # build + testes
./mvnw spring-boot:run
```

Os testes de integração usam **Testcontainers** (PostgreSQL real), portanto é necessário Docker em execução.

## Decisões técnicas

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
