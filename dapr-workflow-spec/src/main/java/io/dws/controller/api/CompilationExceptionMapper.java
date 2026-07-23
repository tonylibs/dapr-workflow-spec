package io.dws.controller.api;

import io.dws.controller.compile.CompilationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Rejected definitions become 400 with the full validation error list; nothing is applied. */
@Provider
public class CompilationExceptionMapper implements ExceptionMapper<CompilationException> {

    @Override
    public Response toResponse(CompilationException exception) {
        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse("Workflow definition is invalid", exception.errors()))
                .build();
    }
}
