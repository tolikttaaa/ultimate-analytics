package com.ttaaa.ultimate.app.web

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

/** A requested resource does not exist: 404. */
class NotFoundException(message: String) : RuntimeException(message)

/** The request conflicts with the current state, e.g. overlapping segments or a duplicate code: 409. */
class ConflictException(message: String) : RuntimeException(message)

/**
 * Errors as RFC 7807 `application/problem+json` (spec 9). Spring's own exceptions are handled by the base class;
 * invalid values rejected by domain constructors (`require`) become 400.
 */
@RestControllerAdvice
class ApiErrors : ResponseEntityExceptionHandler() {

    @ExceptionHandler
    fun notFound(e: NotFoundException): ProblemDetail = problem(HttpStatus.NOT_FOUND, e.message)

    @ExceptionHandler
    fun conflict(e: ConflictException): ProblemDetail = problem(HttpStatus.CONFLICT, e.message)

    @ExceptionHandler
    fun badRequest(e: IllegalArgumentException): ProblemDetail = problem(HttpStatus.BAD_REQUEST, e.message)

    private fun problem(status: HttpStatus, detail: String?) = ProblemDetail.forStatusAndDetail(status, detail)
}
