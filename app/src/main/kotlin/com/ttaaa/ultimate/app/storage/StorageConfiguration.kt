package com.ttaaa.ultimate.app.storage

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.file.Path

/** `app.storage.*` (spec 11). */
@ConfigurationProperties("app.storage")
data class StorageProperties(
    /** [RawFileStore] implementation; `filesystem` in the POC, S3-compatible storage later (spec 8.3). */
    val type: String = "filesystem",
    val rawDir: Path = Path.of("storage/raw"),
)

/** The only place that chooses the [RawFileStore] implementation (spec 7.5, rule 2). */
@Configuration
class StorageConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "app.storage", name = ["type"], havingValue = "filesystem", matchIfMissing = true)
    fun filesystemRawFileStore(properties: StorageProperties): RawFileStore = FilesystemRawFileStore(properties.rawDir)
}
