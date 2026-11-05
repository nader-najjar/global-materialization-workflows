FROM debian:trixie-slim@sha256:f6b08b09a05725f9431502d3b5714f5c4576e82c1a84fa1debe0c24a7e0c3ec4

RUN ln -s /custom-software/runtime-artifacts/nix /nix

COPY . /custom-software/

USER 10001:10001

ENTRYPOINT ["/custom-software/symlink-to-absolute-ignite-script/bin/ignite", "/custom-software/idiomatic-artifacts"]
