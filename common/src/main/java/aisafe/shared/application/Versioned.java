package aisafe.shared.application;

/**
 * A value together with its version (the one its ETag is built from).
 */
public record Versioned<T>(T value, Long version) {
}
