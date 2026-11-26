
package com.medirag.backend.config;

import org.opencv.core.Core;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import nu.pattern.OpenCV;

@Configuration
public class OpenCvConfig {

    private static final Logger log =
            LoggerFactory.getLogger(OpenCvConfig.class);

    @Bean
    public OpenCvRuntime openCvRuntime() {

        try {

            OpenCV.loadLocally();

            String version = Core.VERSION;

            log.info(
                    "OpenCV initialized successfully. Version: {}",
                    version
            );

            return new OpenCvRuntime(version);

        } catch (Exception | LinkageError e) {

            throw new IllegalStateException(
                    "Failed to initialize OpenCV native library",
                    e
            );
        }
    }

    public record OpenCvRuntime(String version) {
    }
}
