package br.com.digidata.crud.controller;

import br.com.digidata.crud.controller.dto.request.IRequest;
import br.com.digidata.crud.controller.dto.response.IResponse;
import br.com.digidata.crud.service.ICrudService;

import java.util.List;
import java.util.UUID;

public class CrudController<Request , Response, Model> implements ICrudController<Request, Response>{

    public final ICrudService<Model, UUID> service;

    public final IRequest<Request, Model> request;

    public final IResponse<Model, Response> response;

    public CrudController(ICrudService<Model, UUID> service, IRequest<Request, Model> request, IResponse<Model, Response> response){
        this.service = service;
        this.request = request;
        this.response = response;
    }

    @Override
    public Response create(Request request) {
        return response.to(service.create(this.request.to(request)));
    }

    @Override
    public Response update(Request request, UUID id) {
        return response.to(service.update(id, this.request.to(request)));
    }

    @Override
    public List<Response> list() {
        return response.to(service.findAll());
    }

    @Override
    public Response findById(UUID id){
        return response.to(service.findById(id));
    }

    @Override
    public void delete(UUID id) {
        service.delete(id);
    }
}
