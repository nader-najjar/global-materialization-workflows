# global-materialization-workflows

Reusable GitHub Actions workflows and actions for repository builds and software materializers.

The action entrypoints run Java through Bazel. Bazel supplies the pinned Java 25 toolchain.

```sh
bazel build //...
bazel test //...
```

The checks include behavior tests, Checkstyle, and SpotBugs.

## License

All rights reserved. See [LICENSE](LICENSE).
