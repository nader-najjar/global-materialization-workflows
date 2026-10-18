from __future__ import annotations

from dataclasses import asdict, dataclass
import json
import os
from pathlib import Path
import sys

import github_state
import source_validation


@dataclass(frozen=True)
class Source:
    source_repository: str
    build_system: str
    runners: list[str]


@dataclass(frozen=True)
class Build:
    source_repository: str
    source_sha: str
    build_system: str
    runner: str


@dataclass(frozen=True)
class SelectionRequest:
    selection_mode: str
    source_repository: str | None
    source_sha: str | None


def load_repositories() -> list[Source]:
    registry = json.loads(Path(".github/repositories.json").read_text(encoding="utf-8"))
    source_validation.validate_registry(registry)

    repositories = [
        Source(source["source_repository"], source["build_system"], source["runners"])
        for source in registry
    ]
    source_validation.validate_repositories(repositories)

    return repositories


def read_request(repositories: list[Source]) -> SelectionRequest:
    selection_mode = os.environ["SELECTION_MODE"]
    source_repository = os.environ.get("SOURCE_REPOSITORY") or None
    source_sha = os.environ.get("SOURCE_SHA") or None
    source_validation.validate_request(
        selection_mode,
        source_repository,
        source_sha,
        repositories
    )

    return SelectionRequest(selection_mode, source_repository, source_sha)


def select_pins(saved_pins: dict[str, str], request: SelectionRequest) -> dict[str, str]:
    pins = saved_pins.copy()
    source_validation.validate_pins(pins)

    if request.source_repository is not None:
        pins[request.source_repository] = request.source_sha

    return pins


def generate_build_matrix(repositories: list[Source], pins: dict[str, str]) -> list[Build]:
    source_validation.validate_initialized_pins(repositories, pins)

    builds = []
    for source in sorted(repositories, key=lambda source: source.source_repository):
        builds.extend(builds_for_source(source, pins[source.source_repository]))

    return builds


def builds_for_source(source: Source, sha: str) -> list[Build]:
    return [
        Build(
            source_repository=source.source_repository,
            source_sha=sha,
            build_system=source.build_system,
            runner=runner
        )
        for runner in sorted(source.runners)
    ]


def write_output(builds: list[Build]) -> None:
    with Path(os.environ["GITHUB_OUTPUT"]).open("a", encoding="utf-8") as output:
        output.write(
            "builds="
            + json.dumps([asdict(build) for build in builds], separators=(",", ":"))
            + "\n"
        )


def main() -> None:
    repositories = load_repositories()
    request = read_request(repositories)

    saved = github_state.load()
    pins = select_pins(saved.revisions, request)
    if request.selection_mode == "materialization":
        github_state.save(pins, saved)

    source_validation.validate_initialization(repositories, pins, request)

    build_matrix = generate_build_matrix(repositories, pins)
    write_output(build_matrix)


if __name__ == "__main__":
    try:
        main()
    except (ValueError, KeyError, TypeError, OSError, RuntimeError) as error:
        sys.exit(str(error))
