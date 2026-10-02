package com.ttaaa.ultimate.app

import java.sql.ResultSet
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/** JDBC binds `timestamptz` as [OffsetDateTime]; the domain uses [Instant]. */
internal fun Instant.toUtc(): OffsetDateTime = atOffset(ZoneOffset.UTC)

internal fun ResultSet.instant(column: String): Instant = getObject(column, OffsetDateTime::class.java).toInstant()

internal fun ResultSet.uuid(column: String): UUID = getObject(column, UUID::class.java)

internal fun ResultSet.uuidOrNull(column: String): UUID? = getObject(column, UUID::class.java)

internal fun ResultSet.intOrNull(column: String): Int? = getInt(column).takeUnless { wasNull() }

internal fun ResultSet.doubleOrNull(column: String): Double? = getDouble(column).takeUnless { wasNull() }

internal fun ResultSet.floatOrNull(column: String): Double? = getFloat(column).takeUnless { wasNull() }?.toDouble()
