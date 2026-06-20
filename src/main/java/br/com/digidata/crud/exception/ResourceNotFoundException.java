package br.com.digidata.crud.exception;

public class ResourceNotFoundException
        extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
