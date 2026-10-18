from urllib.error import HTTPError


def handle_http_error(error: HTTPError, method: str, path: str, *, missing_ok: bool) -> None:
    if missing_ok and error.code == 404:
        return None
    raise RuntimeError(f"GitHub state request failed: {method} {path} returned {error.code}") from error


def validate_encoding(response: dict) -> None:
    if response["encoding"] != "base64":
        raise ValueError("Source pins must use base64 encoding")


def validate_saved_pins(pins: dict) -> None:
    if not isinstance(pins, dict):
        raise ValueError("Saved source pins must be a JSON object")
