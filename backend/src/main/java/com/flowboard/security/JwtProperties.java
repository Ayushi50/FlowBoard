package com.flowboard.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("flowboard.jwt")
public record JwtProperties(String secret, long ttlMinutes) {
}
