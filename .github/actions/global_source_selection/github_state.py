import base64
from dataclasses import dataclass
import json
import os
from urllib.error import HTTPError
from urllib.request import Request, urlopen

import github_state_validation


BRANCH = "materialization-state"
ENDPOINT = f"{os.environ['GITHUB_API_URL']}/repos/{os.environ['GITHUB_REPOSITORY']}"
HEADERS = {
    "Authorization": f"Bearer {os.environ['GH_TOKEN']}",
    "Accept": "application/vnd.github+json",
    "Content-Type": "application/json",
    "X-GitHub-Api-Version": "2022-11-28"
}


@dataclass(frozen=True)
class SavedPins:
    revisions: dict[str, str]
    blob_sha: str | None


def _request(method: str, path: str, payload: dict | None = None, *, missing_ok: bool = False) -> dict | None:
    data = json.dumps(payload).encode("utf-8") if payload is not None else None
    request = Request(ENDPOINT + path, data=data, headers=HEADERS, method=method)

    try:
        return _read_response(request)
    except HTTPError as error:
        return github_state_validation.handle_http_error(error, method, path, missing_ok=missing_ok)


def _read_response(request: Request) -> dict:
    with urlopen(request, timeout=30) as response:
        return json.load(response)


def load() -> SavedPins:
    response = _request("GET", f"/contents/source-pins.json?ref={BRANCH}", missing_ok=True)
    if response is None:
        return SavedPins({}, None)
    github_state_validation.validate_encoding(response)

    content = base64.b64decode("".join(response["content"].split()), validate=True)
    pins = json.loads(content)
    github_state_validation.validate_saved_pins(pins)

    return SavedPins(pins, response["sha"])


def save(pins: dict[str, str], previous: SavedPins) -> None:
    _ensure_state_branch(previous)

    payload = {
        "branch": BRANCH,
        "message": "Update materialization source pins",
        "content": base64.b64encode(json.dumps(pins, sort_keys=True).encode("utf-8")).decode("ascii")
    }
    if previous.blob_sha is not None:
        payload["sha"] = previous.blob_sha

    _request("PUT", "/contents/source-pins.json", payload)


def _ensure_state_branch(previous: SavedPins) -> None:
    if previous.blob_sha is not None:
        return

    branch = _request("GET", f"/git/ref/heads/{BRANCH}", missing_ok=True)
    if branch is not None:
        return

    payload = {
        "ref": f"refs/heads/{BRANCH}",
        "sha": os.environ["GITHUB_SHA"]
    }
    _request("POST", "/git/refs", payload)
