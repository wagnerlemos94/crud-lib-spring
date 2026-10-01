package br.com.digidata.crud.controller;

import br.com.digidata.crud.controller.dto.request.IRequest;
import br.com.digidata.crud.controller.dto.response.IResponse;
import br.com.digidata.crud.service.ICrudService;

import br.com.digidata.crud.controller.dto.response.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

public class CrudController<Request, Response, Model, ID> implements ICrudController<Request, Response, ID>{

    public final ICrudService<Model, ID> service;

    public final IRequest<Request, Model> request;

    public final IResponse<Model, Response> response;

    public CrudController(ICrudService<Model, ID> service, IRequest<Request, Model> request, IResponse<Model, Response> response){
        this.service = service;
        this.request = request;
        this.response = response;
    }

    @Override
    public Response create(Request request) {
        return response.to(service.create(this.request.to(request)));
    }

    @Override
    public Response update(Request request, ID id) {
        return response.to(service.update(id, this.request.to(request)));
    }

    @Override
    public PageResponse<Response> list(Pageable pageable) {
        Pageable bounded = pageable.isUnpaged()
                ? PageRequest.of(0, 20, pageable.getSort())
                : PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 100), pageable.getSort());
        return PageResponse.from(service.findAll(bounded).map(response::to));
    }

    @Override
    public Response findById(ID id){
        return response.to(service.findById(id));
    }

    @Override
    public void delete(ID id) {
        service.delete(id);
    }
}
