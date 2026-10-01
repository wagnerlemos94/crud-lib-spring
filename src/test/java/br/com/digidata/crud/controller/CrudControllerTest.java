package br.com.digidata.crud.controller;

import br.com.digidata.crud.controller.dto.request.IRequest;
import br.com.digidata.crud.controller.dto.response.IResponse;
import br.com.digidata.crud.exception.ResourceNotFoundException;
import br.com.digidata.crud.service.ICrudService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class CrudControllerTest {
    private final StubService<UUID> service = new StubService<>();
    private final StubService<Long> numericService = new StubService<>();
    private final LocalValidatorFactoryBean validator = validator();
    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new TestController(service), new NumericController(numericService))
            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
            .setValidator(validator).build();

    private static LocalValidatorFactoryBean validator() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.setMessageInterpolator(new ParameterMessageInterpolator());
        validator.afterPropertiesSet();
        return validator;
    }

    @AfterEach
    void closeValidator() { validator.close(); }

    @Test
    void deleteRouteBindsUuidAndReturns204WithoutBody() throws Exception {
        UUID id = UUID.randomUUID();
        var result = mvc.perform(delete("/items/{id}", id)).andReturn().getResponse();
        assertEquals(204, result.getStatus());
        assertEquals("", result.getContentAsString());
        assertEquals(id, service.deleted);
    }

    @Test
    void numericIdIsConvertedForGetUpdateAndDelete() throws Exception {
        assertEquals(200, mvc.perform(get("/numeric/42")).andReturn().getResponse().getStatus());
        assertEquals(42L, numericService.requestedId);
        assertEquals(200, mvc.perform(put("/numeric/43").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"updated\"}")).andReturn().getResponse().getStatus());
        assertEquals(43L, numericService.requestedId);
        assertEquals("updated", numericService.written.name());
        assertEquals(204, mvc.perform(delete("/numeric/44")).andReturn().getResponse().getStatus());
        assertEquals(44L, numericService.deleted);
    }

    @Test
    void invalidIdReturns400WithoutInvokingService() throws Exception {
        assertEquals(400, mvc.perform(get("/numeric/invalid")).andReturn().getResponse().getStatus());
        assertNull(numericService.requestedId);
        assertEquals(400, mvc.perform(delete("/items/invalid")).andReturn().getResponse().getStatus());
        assertNull(service.deleted);
    }

    @Test
    void missingOperationsReturn404() throws Exception {
        service.missing = true;
        UUID id = UUID.randomUUID();
        assertEquals(404, mvc.perform(get("/items/{id}", id)).andReturn().getResponse().getStatus());
        assertEquals(404, mvc.perform(delete("/items/{id}", id)).andReturn().getResponse().getStatus());
        assertEquals(404, mvc.perform(put("/items/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"valid\"}")).andReturn().getResponse().getStatus());
    }

    @Test
    void createReturns201AndMappedResponse() throws Exception {
        var response = mvc.perform(post("/items").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"valid\"}")).andReturn().getResponse();
        assertEquals(201, response.getStatus());
        assertEquals("VALID", new ObjectMapper().readTree(response.getContentAsString()).get("name").asText());
        assertEquals("valid", service.written.name());
    }

    @Test
    void validationRejectsCreateAndUpdateBeforeCallingService() throws Exception {
        assertEquals(400, mvc.perform(post("/items").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\" \"}")).andReturn().getResponse().getStatus());
        assertEquals(400, mvc.perform(put("/items/{id}", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andReturn().getResponse().getStatus());
        assertNull(service.written);
        assertNull(service.requestedId);
    }

    @Test
    void listUsesBoundedSortedPageAndMapsContentWithMetadata() throws Exception {
        var response = mvc.perform(get("/items?page=1&size=2&sort=name,desc"))
                .andReturn().getResponse();
        assertEquals(200, response.getStatus());
        assertEquals(PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "name")), service.requestedPage);
        JsonNode body = new ObjectMapper().readTree(response.getContentAsString());
        assertEquals("ITEM", body.get("content").get(0).get("name").asText());
        assertEquals(1, body.get("page").asInt());
        assertEquals(2, body.get("size").asInt());
        assertEquals(5, body.get("totalElements").asInt());
        assertEquals(3, body.get("totalPages").asInt());
    }

    @Test
    void listDefaultsAndMaximumPreventUnboundedHttpQueries() throws Exception {
        assertEquals(200, mvc.perform(get("/items")).andReturn().getResponse().getStatus());
        assertEquals(PageRequest.of(0, 20), service.requestedPage);
        mvc.perform(get("/items?size=10000"));
        assertEquals(100, service.requestedPage.getPageSize());
        new TestController(service).list(Pageable.unpaged());
        assertEquals(PageRequest.of(0, 20), service.requestedPage);
    }

    @Test
    void emptyPageRetainsMetadata() throws Exception {
        service.empty = true;
        var response = mvc.perform(get("/items?page=3&size=2")).andReturn().getResponse();
        JsonNode body = new ObjectMapper().readTree(response.getContentAsString());
        assertTrue(body.get("content").isEmpty());
        assertEquals(3, body.get("page").asInt());
        assertEquals(5, body.get("totalElements").asInt());
    }

    public record Input(@NotBlank String name) {}
    public record Output(String name) {}

    private static class RequestMapper implements IRequest<Input, Input> {
        public Input to(Input value) { return value; }
        public List<Input> to(List<Input> values) { return values; }
    }
    private static class ResponseMapper implements IResponse<Input, Output> {
        public Output to(Input value) { return new Output(value.name().toUpperCase(java.util.Locale.ROOT)); }
        public List<Output> to(List<Input> values) { return values.stream().map(this::to).toList(); }
    }

    @RestController
    @RequestMapping("/items")
    public static class TestController extends CrudController<Input, Output, Input, UUID> {
        TestController(StubService<UUID> service) { super(service, new RequestMapper(), new ResponseMapper()); }
    }
    @RestController
    @RequestMapping("/numeric")
    public static class NumericController extends CrudController<Input, Output, Input, Long> {
        NumericController(StubService<Long> service) { super(service, new RequestMapper(), new ResponseMapper()); }
    }

    private static class StubService<ID> implements ICrudService<Input, ID> {
        ID deleted;
        ID requestedId;
        boolean missing;
        boolean empty;
        Input written;
        Pageable requestedPage;
        public Input findById(ID id) {
            requestedId = id;
            if (missing) throw new ResourceNotFoundException("Registro não encontrado");
            return new Input("item");
        }
        public List<Input> findAll() { throw new AssertionError("HTTP must use pagination"); }
        public Page<Input> findAll(Pageable pageable) {
            requestedPage = pageable;
            return new PageImpl<>(empty ? List.of() : List.of(new Input("item")), pageable, 5);
        }
        public Input create(Input entity) { written = entity; return entity; }
        public Input update(ID id, Input entity) { findById(id); written = entity; return entity; }
        public void delete(ID id) { findById(id); deleted = id; }
    }
}
