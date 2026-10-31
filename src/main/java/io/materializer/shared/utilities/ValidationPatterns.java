package io.materializer.shared.utilities;

public final class ValidationPatterns {

    public static final String REPOSITORY = "[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+";
    public static final String SHA = "[a-f0-9]{40}";

    private ValidationPatterns() { }
}
