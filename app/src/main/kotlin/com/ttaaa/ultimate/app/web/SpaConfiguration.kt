package com.ttaaa.ultimate.app.web

import org.springframework.context.annotation.Configuration
import org.springframework.core.io.Resource
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.resource.PathResourceResolver

/**
 * Serves the SPA from `classpath:/static/` (spec 7.1). Client-side routes such as `/sessions/{id}` get `index.html`, so
 * deep links and reloads work; backend paths and missing files keep their 404.
 */
@Configuration
class SpaConfiguration : WebMvcConfigurer {

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry.addResourceHandler("/**")
            .addResourceLocations("classpath:/static/")
            .resourceChain(true)
            .addResolver(SpaResourceResolver())
    }

    private class SpaResourceResolver : PathResourceResolver() {
        override fun getResource(resourcePath: String, location: Resource): Resource? {
            val resource = location.createRelative(resourcePath)
            if (resource.exists() && resource.isReadable) return resource
            val backendPath = BACKEND_PREFIXES.any { resourcePath.startsWith(it) }
            val fileName = resourcePath.substringAfterLast('/')
            if (backendPath || '.' in fileName) return null
            return location.createRelative("index.html").takeIf { it.exists() }
        }
    }

    private companion object {
        val BACKEND_PREFIXES = listOf("api/", "actuator", "v3/", "swagger-ui")
    }
}
