from __future__ import annotations

import re
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from select_sources import SelectionRequest, Source


SELECTION_MODES = {"dry_run", "materialization", "bounce"}


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
        isinstance(source.build_system, str) and bool(source.build_system),
        "Source build system is required"
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
    selection_mode: str,
    source_repository: str | None,
    source_sha: str | None,
    repositories: list[Source]
) -> None:
    require(
        selection_mode in SELECTION_MODES,
        "Selection mode must be dry_run, materialization, or bounce"
    )

    has_repository = source_repository is not None
    has_sha = source_sha is not None
    require(
        has_repository == has_sha,
        "Provide source_repository and source_sha together"
    )

    if selection_mode == "bounce":
        require(
            not has_repository,
            "A bounce uses current pins; omit source_repository and source_sha"
        )
        return

    require(
        has_repository,
        "Supply source_repository and source_sha for dry_run or materialization"
    )
    require(
        re.fullmatch(r"[a-f0-9]{40}", source_sha) is not None,
        "source_sha must be a full lowercase commit SHA"
    )
    require(
        any(source.source_repository == source_repository for source in repositories),
        "Source repository is not registered"
    )


def validate_pins(pins: dict[str, str]) -> None:
    require(isinstance(pins, dict), "Saved source pins must be a JSON object")
    require(
        all(
            isinstance(sha, str) and re.fullmatch(r"[a-f0-9]{40}", sha) is not None
            for sha in pins.values()
        ),
        "Saved source revisions must be full commit SHAs"
    )


def uninitialized_sources(repositories: list[Source], pins: dict[str, str]) -> list[str]:
    return sorted(
        source.source_repository
        for source in repositories
        if source.source_repository not in pins
    )


def validate_initialization(
    repositories: list[Source],
    pins: dict[str, str],
    request: SelectionRequest
) -> None:
    missing = uninitialized_sources(repositories, pins)
    if missing:
        names = ", ".join(missing)
        if request.selection_mode == "materialization":
            raise ValueError(
                "Initialization incomplete: saved pin for "
                f"{request.source_repository} at {request.source_sha}; "
                f"source pins are still missing for: {names}. No builds were started."
            )
        raise ValueError(
            f"Source pins are not initialized for: {names}. No builds were started."
        )


def validate_initialized_pins(repositories: list[Source], pins: dict[str, str]) -> None:
    missing = uninitialized_sources(repositories, pins)
    require(not missing, "Source pins are not initialized for: " + ", ".join(missing))


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)
