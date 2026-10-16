from __future__ import annotations

import re
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from select_sources import Source


def validate_registry(registry: list) -> None:
    require(isinstance(registry, list), "Source registry must be a JSON list")
    require(
        all(isinstance(source, dict) for source in registry),
        "Source definition must be a JSON object"
    )


def validate_repositories(repositories: list[Source]) -> None:
    for source in repositories:
        validate_source(source)

    names = [source.source_repository for source in repositories]
    require(len(names) == len(set(names)), "Source repositories must be unique")


def validate_source(source: Source) -> None:
    require(
        isinstance(source.source_repository, str) and bool(source.source_repository),
        "Source repository is required"
    )

    require(
        source.build_system == "universal",
        "Source build system is not implemented"
    )

    require(
        isinstance(source.runners, list) and bool(source.runners),
        "Source runners must be a nonempty list"
    )

    require(
        all(isinstance(runner, str) and bool(runner) for runner in source.runners),
        "Source runner is required"
    )

    require(
        len(source.runners) == len(set(source.runners)),
        "Source runners must be unique"
    )


def validate_request(
    bounce: str,
    source_repository: str,
    source_sha: str,
    repositories: list[Source]
) -> None:
    if bounce == "true":
        require(
            not source_repository and not source_sha,
            "A bounce uses current pins; omit source_repository and source_sha"
        )
        return
    require(bounce == "false", "Bounce must be true or false")
    require(
        bool(source_repository) and re.fullmatch(r"[a-f0-9]{40}", source_sha) is not None,
        "Supply source_repository and a full source_sha when bounce is false"
    )
    require(
        any(source.source_repository == source_repository for source in repositories),
        "Source repository is not registered"
    )


def validate_pins(pins: dict[str, str]) -> None:
    require(
        all(isinstance(sha, str) and re.fullmatch(r"[a-f0-9]{40}", sha) is not None for sha in pins.values()),
        "Saved source revisions must be full commit SHAs"
    )


def validate_initialized_pins(repositories: list[Source], pins: dict[str, str]) -> None:
    missing = sorted(source.source_repository for source in repositories if source.source_repository not in pins)
    require(not missing, "Source pins are not initialized for: " + ", ".join(missing))


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)
