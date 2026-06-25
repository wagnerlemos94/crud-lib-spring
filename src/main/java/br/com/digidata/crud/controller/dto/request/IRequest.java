package br.com.digidata.crud.controller.dto.request;

import java.util.List;

public interface IRequest<Request, Model>{
    Model to(Request request);
    List<Model> to(List<Request> requests);
}
