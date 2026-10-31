package io.materializer.snapshots.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SnapshotHeader(@JsonProperty("schema_version") Integer schemaVersion) { }
