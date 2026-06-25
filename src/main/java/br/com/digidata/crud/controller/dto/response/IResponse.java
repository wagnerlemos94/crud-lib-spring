package br.com.digidata.crud.controller.dto.response;

import java.util.List;

public interface IResponse<Model, Response>{
    Response to(Model model);
    List<Response> to(List<Model> model);
}
