package br.com.digidata.crud.controller;

import br.com.digidata.crud.controller.dto.response.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;

public interface ICrudController<Request, Response, ID> {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Response create(@Valid @RequestBody Request request);
    /** Replaces the writable fields, including nulls; omitted fields are not preserved. */
    @PutMapping({"{id}"})
    Response update(@Valid @RequestBody Request request, @PathVariable("id") ID id);
    @GetMapping
    PageResponse<Response> list(@PageableDefault(size = 20) Pageable pageable);
    @GetMapping("{id}")
    Response findById(@PathVariable("id") ID id);
    @DeleteMapping("{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable("id") ID id);
}
