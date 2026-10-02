package com.ttaaa.ultimate.app

/** A requested resource does not exist: 404. */
class NotFoundException(message: String) : RuntimeException(message)

/** The request conflicts with the current state, e.g. overlapping segments or a duplicate code: 409. */
class ConflictException(message: String) : RuntimeException(message)
