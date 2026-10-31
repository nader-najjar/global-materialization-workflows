load("@aspect_rules_lint//lint:checkstyle.bzl", "lint_checkstyle_aspect")
load("@aspect_rules_lint//lint:lint_test.bzl", "lint_test")
load("@aspect_rules_lint//lint:spotbugs.bzl", "lint_spotbugs_aspect")

checkstyle = lint_checkstyle_aspect(
    binary = Label("//tools/lint:checkstyle"),
    config = Label("//:checkstyle.xml"),
    rule_kinds = ["java_library", "java_test"],
)

spotbugs = lint_spotbugs_aspect(
    binary = Label("//tools/lint:spotbugs"),
    exclude_filter = Label("//:spotbugs-exclude.xml"),
)

checkstyle_test = lint_test(aspect = checkstyle)
spotbugs_test = lint_test(aspect = spotbugs)
