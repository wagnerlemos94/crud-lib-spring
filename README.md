# crud-core

Biblioteca Java para reutilizar operações CRUD em aplicações Spring com Spring Data JPA. Fornece classes base para serviços e controllers, interfaces de conversão entre DTOs e entidades e uma exceção para registros não encontrados.

A biblioteca é distribuída como JAR; não é uma aplicação executável e não configura servidor HTTP ou banco de dados.

## Requisitos

- Java 21 ou superior, com compilação direcionada ao Java 21.
- Maven para compilar, testar e instalar a biblioteca.
- Aplicação consumidora com Spring MVC, Spring Data JPA, provedor JPA, driver e conexão com o banco configurados.
- Serviços registrados como beans Spring e gerenciamento de transações habilitado.

O `pom.xml` declara Spring Web/Context 6.2.7 e Spring Data JPA 3.4.7. A aplicação consumidora deve manter suas dependências compatíveis. Em aplicações Spring Boot, os starters de Web e Data JPA fornecem a infraestrutura correspondente.

Para validar os DTOs, inclua `spring-boot-starter-validation` na aplicação Spring Boot, com a versão gerenciada pelo seu projeto. Em Spring MVC sem Boot, configure um provedor Jakarta Bean Validation e um validador MVC. A biblioteca fornece a API de validação, mas não impõe um provedor em produção.

## Instalação

Para disponibilizar a versão no repositório Maven local, execute na raiz desta biblioteca:

```sh
mvn install
```

Adicione a dependência ao `pom.xml` da aplicação consumidora:

```xml
<dependency>
    <groupId>br.com.digidata</groupId>
    <artifactId>crud-core</artifactId>
    <version>2.0.0</version>
</dependency>
```

O destino de publicação configurado no projeto é o GitHub Packages. Para consumir uma versão já publicada nesse destino, configure também o repositório:

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/wagnerlemos94/crud-lib-spring</url>
    </repository>
</repositories>
```

Configure as credenciais necessárias no `settings.xml` do Maven, usando um servidor com o mesmo ID `github`. Não armazene credenciais no projeto. A alteração da versão no código não publica o artefato automaticamente.

## Componentes

| Componente | Responsabilidade |
| --- | --- |
| `ICrudService<T, ID>` | Contrato de busca, listagem, criação, atualização e exclusão. |
| `CrudService<T, ID>` | Implementação base sobre `JpaRepository`, com transações e seleção explícita dos campos atualizáveis. |
| `ICrudController<Request, Response, ID>` | Contrato HTTP com mapeamentos CRUD, validação de entrada e listagem paginada. |
| `CrudController<Request, Response, Model, ID>` | Conversão de DTOs e delegação ao serviço. O tipo de identificador é definido pela aplicação. |
| `PageResponse<T>` | Resposta paginada com conteúdo, número e tamanho da página e totais. |
| `IRequest<Request, Model>` | Conversão de uma requisição ou lista de requisições em entidades. |
| `IResponse<Model, Response>` | Conversão de uma entidade ou lista de entidades em respostas. |
| `ResourceNotFoundException` | Exceção de registro inexistente, anotada para retornar HTTP 404 no Spring MVC. |

## Exemplo de uso

Os arquivos abaixo pertencem à aplicação consumidora. O exemplo usa o pacote `com.example.produto`, que deve estar dentro dos pacotes escaneados pela aplicação Spring.

### 1. Entidade e repositório

`Produto.java`:

```java
package com.example.produto;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
public class Produto {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    private Long versao;

    private String nome;
    private String descricao;
    private BigDecimal preco;

    public UUID getId() { return id; }
    public Long getVersao() { return versao; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public BigDecimal getPreco() { return preco; }
    public void setPreco(BigDecimal preco) { this.preco = preco; }
}
```

`ProdutoRepository.java`:

```java
package com.example.produto;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProdutoRepository extends JpaRepository<Produto, UUID> {
}
```

### 2. DTOs

`ProdutoRequest.java`:

```java
package com.example.produto;

import java.math.BigDecimal;

public record ProdutoRequest(
        @jakarta.validation.constraints.NotBlank String nome,
        String descricao,
        @jakarta.validation.constraints.NotNull
        @jakarta.validation.constraints.PositiveOrZero BigDecimal preco) {
}
```

`ProdutoResponse.java`:

```java
package com.example.produto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProdutoResponse(UUID id, String nome, String descricao, BigDecimal preco) {
}
```

### 3. Conversores

As duas interfaces exigem implementação para objetos individuais e listas.

`ProdutoRequestMapper.java`:

```java
package com.example.produto;

import br.com.digidata.crud.controller.dto.request.IRequest;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ProdutoRequestMapper implements IRequest<ProdutoRequest, Produto> {
    @Override
    public Produto to(ProdutoRequest request) {
        Produto produto = new Produto();
        produto.setNome(request.nome());
        produto.setDescricao(request.descricao());
        produto.setPreco(request.preco());
        return produto;
    }

    @Override
    public List<Produto> to(List<ProdutoRequest> requests) {
        return requests.stream().map(this::to).toList();
    }
}
```

`ProdutoResponseMapper.java`:

```java
package com.example.produto;

import br.com.digidata.crud.controller.dto.response.IResponse;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ProdutoResponseMapper implements IResponse<Produto, ProdutoResponse> {
    @Override
    public ProdutoResponse to(Produto produto) {
        return new ProdutoResponse(
                produto.getId(), produto.getNome(), produto.getDescricao(), produto.getPreco());
    }

    @Override
    public List<ProdutoResponse> to(List<Produto> produtos) {
        return produtos.stream().map(this::to).toList();
    }
}
```

### 4. Serviço

`ProdutoService.java`:

```java
package com.example.produto;

import br.com.digidata.crud.service.CrudService;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ProdutoService extends CrudService<Produto, UUID> {
    public ProdutoService(ProdutoRepository repository) {
        super(repository);
    }

    @Override
    protected Set<String> updatableProperties() {
        return Set.of("nome", "descricao", "preco");
    }
}
```

`updatableProperties()` é um método abstrato protegido da classe base. Toda subclasse concreta deve implementá-lo, a menos que herde uma implementação de uma classe intermediária. Ele configura a atualização e não faz parte da interface pública `ICrudService`.

Use nomes de propriedades JavaBean, com getters na origem e setters no destino. Propriedades inválidas causam `IllegalArgumentException` antes da cópia. Não inclua identificadores, versões, campos de auditoria ou relacionamentos protegidos: a seleção desses campos é responsabilidade do serviço concreto. A biblioteca não identifica automaticamente campos protegidos por suas anotações.

### 5. Controller

`ProdutoController.java`:

```java
package com.example.produto;

import br.com.digidata.crud.controller.CrudController;
import java.util.UUID;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/produtos")
public class ProdutoController
        extends CrudController<ProdutoRequest, ProdutoResponse, Produto, UUID> {

    public ProdutoController(
            ProdutoService service,
            ProdutoRequestMapper requestMapper,
            ProdutoResponseMapper responseMapper) {
        super(service, requestMapper, responseMapper);
    }
}
```

Os mapeamentos HTTP são herdados do contrato da biblioteca. O controller concreto define a rota base e é registrado como bean pela anotação `@RestController`.

Para entidades com chave numérica, use `Long` no repositório, serviço e no quarto parâmetro genérico do controller. Tipos personalizados de ID precisam de um conversor registrado no Spring MVC.

## Endpoints

Para o controller do exemplo, sem customizações adicionais:

| Método | Rota | Operação | Resposta de sucesso |
| --- | --- | --- | --- |
| `POST` | `/produtos` | Cria um produto. | 201 com o DTO de resposta. |
| `PUT` | `/produtos/{id}` | Substitui os campos permitidos de um produto existente. | 200 com o DTO de resposta. |
| `GET` | `/produtos` | Lista uma página de produtos. | 200 com `PageResponse<ProdutoResponse>`. |
| `GET` | `/produtos/{id}` | Busca um produto por UUID. | 200 com o DTO de resposta. |
| `DELETE` | `/produtos/{id}` | Exclui um produto existente. | 204 sem corpo. |

Busca, atualização e exclusão de um registro inexistente lançam `ResourceNotFoundException`, traduzida para HTTP 404 pelo Spring MVC. A biblioteca não define um formato próprio para o corpo dos erros; a aplicação consumidora pode personalizá-lo.

`POST` e `PUT` usam `@Valid`: as restrições declaradas no DTO são avaliadas antes de chamar o serviço. Com o provedor de validação configurado, entradas inválidas retornam HTTP 400. A aplicação continua responsável por declarar as restrições dos seus DTOs. Chamadas diretas ao serviço não passam pela validação HTTP.

## Paginação e ordenação

Exemplo de requisição:

```http
GET /produtos?page=0&size=20&sort=nome,asc
```

- `page`: índice da página, começando em zero.
- `size`: 20 por padrão, limitado pelo controller a 100 itens por página.
- `sort`: propriedade da entidade e direção; pode ser repetido para ordenar por mais de um campo.

A paginação é enviada ao `JpaRepository`; o controller não carrega toda a tabela para recortar a lista em memória. Escolha uma ordenação estável, incluindo um campo único quando necessário.

Exemplo de resposta:

```json
{
  "content": [
    { "id": "e8d76d76-657c-433c-b7a7-52272cc927f2", "nome": "Teclado", "descricao": null, "preco": 150.00 }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

O serviço oferece `findAll(Pageable)` para consultas paginadas e mantém `findAll()` para usos internos que precisam de todos os registros. O método paginado rejeita `Pageable.unpaged()`; o limite de 100 é aplicado no controller HTTP.

O parâmetro `Pageable` requer suporte Spring Data Web. Na aplicação Spring MVC sem essa configuração, habilite-o em uma classe de configuração escaneada:

```java
@org.springframework.context.annotation.Configuration
@org.springframework.data.web.config.EnableSpringDataWebSupport
public class PaginationConfiguration {
}
```

Em aplicações Spring Boot com a autoconfiguração de Spring Data Web ativa, esse suporte já é registrado.

## Contrato de atualização

O `PUT` substitui todos os campos de `updatableProperties()` pelos valores da entidade produzida pelo conversor:

- Valores `null` são copiados e podem limpar campos, respeitando as restrições do banco.
- Valores `false` e `0` também são copiados.
- Campos fora da lista são preservados.
- Campos omitidos no JSON não são distinguidos de valores padrão pelo serviço. O resultado depende do DTO e do conversor; neste exemplo, campos de referência omitidos chegam como `null`.
- A cópia é superficial: não há atualização recursiva de relacionamentos ou coleções.

Exemplo de corpo de uma atualização que limpa a descrição:

```json
{
  "nome": "Teclado",
  "descricao": null,
  "preco": 150.00
}
```

Envie o estado completo dos campos editáveis. Este contrato não implementa atualização parcial via `PATCH`. A lista de campos permitidos se aplica apenas à atualização; na criação, o serviço entrega ao repositório a entidade produzida pelo conversor.

## Transações e concorrência

As consultas usam `@Transactional(readOnly = true)`. Criação, atualização e exclusão usam transações de escrita. Na atualização e na exclusão, a busca e a escrita participam da mesma transação quando a chamada passa pelo proxy Spring do serviço.

Instanciar o serviço manualmente com `new` não ativa esse gerenciamento. O serviço deve ser injetado como bean e usar um gerenciador de transações configurado pela aplicação.

Uma transação, por si só, não impede atualizações perdidas. O exemplo inclui `@Version` para detecção de conflitos entre transações concorrentes pelo JPA. Isso não implementa uma comparação com a versão que o cliente HTTP leu anteriormente; controle de versão no contrato HTTP exige implementação adicional na aplicação.

## Migração de 1.2.5 para 2.0.0

A versão principal foi incrementada por mudanças incompatíveis com os consumidores anteriores:

1. Implemente `updatableProperties()` nos serviços concretos e selecione explicitamente os campos editáveis.
2. Ajuste os clientes e conversores para enviar o estado completo desses campos no `PUT`. Antes, valores nulos eram ignorados; agora são copiados.
3. Use `DELETE /recurso/{id}` para a exclusão.
4. Considere a resposta HTTP 404 para registros inexistentes, inclusive em testes e tratamentos de erro próprios.
5. Revise métodos sobrescritos e a configuração de transações da aplicação para preservar o comportamento desejado.
6. Adicione o tipo de ID ao controller (`CrudController<Request, Response, Model, UUID>`) e à interface, se implementada diretamente (`ICrudController<Request, Response, UUID>`).
7. Adapte consumidores da listagem para o objeto paginado (`content`, `page`, `size`, `totalElements`, `totalPages`) e para `list(Pageable)`. Implementações próprias de `ICrudService` precisam implementar `findAll(Pageable)`.
8. Atualize expectativas de status: criação retorna 201 e exclusão retorna 204. Configure o provedor de validação e adicione restrições aos DTOs.

## Desenvolvimento

Execute os testes:

```sh
mvn test
```

Gere o JAR:

```sh
mvn package
```

O artefato será gerado em `target/crud-core-2.0.0.jar`.

Os testes atuais verificam IDs UUID e Long, respostas 201/204/400/404, validação antes da chamada ao serviço, paginação e ordenação, limite de página, mapeamento dos DTOs e metadados, seleção dos campos atualizáveis, cópia de valores nulos e padrões e limites transacionais. Usam MockMvc, um provedor real de Bean Validation, um repositório simulado e um gerenciador de transações de teste; não exercitam um banco de dados real.

O diretório `target/` contém artefatos gerados e está no `.gitignore`.

Para publicar no destino configurado, com as credenciais adequadas:

```sh
mvn deploy
```

## Limites atuais

- A listagem tem paginação e ordenação, mas não oferece filtros de negócio.
- Restrições dos DTOs, autorização e regras de negócio devem ser definidas pela aplicação consumidora.
- A biblioteca não fornece configuração automática de banco, aplicação executável ou tratamento padronizado de todos os erros.
