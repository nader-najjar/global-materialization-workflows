package io.materializer.github.exceptions;

public final class GitHubRequestException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private final int status;

    public GitHubRequestException(int status, String method, String path) {
        super("GitHub request failed: " + method + " " + path + " returned " + status);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
