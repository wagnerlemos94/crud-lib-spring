package br.com.digidata.crud.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;

public interface ICrudController<Request, Response> {
    @PostMapping
    Response create(@RequestBody Request request);
    @PutMapping
    Response update(@RequestBody Request request, @PathVariable UUID id);
    @GetMapping
    List<Response> list();
    @GetMapping("{id}")
    Response findById(@PathVariable UUID id);
    @DeleteMapping
    void delete(@PathVariable UUID id);
}
