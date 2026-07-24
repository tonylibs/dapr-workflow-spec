package io.dws.controller.api;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class WorkflowNotFoundExceptionMapper implements ExceptionMapper<WorkflowNotFoundException> {

    @Override
    public Response toResponse(WorkflowNotFoundException exception) {
        return Response.status(Response.Status.NOT_FOUND)
                .type(MediaType.APPLICATION_JSON)
                .entity(ErrorResponse.of(exception.getMessage()))
                .build();
    }
}
