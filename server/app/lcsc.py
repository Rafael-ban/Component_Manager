from __future__ import annotations

from collections.abc import Iterable
from hashlib import sha1
import json
from threading import Lock
import time
from typing import Any
from urllib.parse import urlencode
from urllib.request import urlopen

from .config import Settings
from .schemas import LcscLookupResponse


class LookupConfigurationError(RuntimeError):
    pass


class LookupRequestError(RuntimeError):
    pass


_PRODUCT_KEYS = {
    "product_number",
    "productNumber",
    "product_model",
    "productModel",
    "mfrPartNumber",
    "brandName",
    "catalogName",
    "encapStandard",
}
_LOOKUP_CACHE: dict[str, tuple[float, LcscLookupResponse]] = {}
_LOOKUP_CACHE_LOCK = Lock()


def lookup_lcsc_product(
    settings: Settings,
    sku: str | None = None,
    mpn: str | None = None,
    name: str | None = None,
) -> LcscLookupResponse:
    normalized_sku = _normalize_text(sku)
    normalized_mpn = _normalize_text(mpn)
    normalized_name = _normalize_text(name)
    if not any((normalized_sku, normalized_mpn, normalized_name)):
        return _empty_lookup()

    _validate_lookup_configuration(settings)

    for matched_by, query in (
        ("sku", normalized_sku),
        ("mpn", normalized_mpn),
        ("name", normalized_name),
    ):
        if not query:
            continue

        cached = _read_cached_lookup(
            settings=settings,
            cache_key=_cache_key(matched_by, query),
        )
        if cached is not None:
            return cached

        if matched_by == "sku":
            response = _lookup_by_product_number(
                settings=settings,
                product_number=query,
                matched_by=matched_by,
            )
        else:
            response = _lookup_by_search(
                settings=settings,
                keyword=query,
                matched_by=matched_by,
            )

        if response.found:
            _write_cached_lookup(
                settings=settings,
                cache_key=_cache_key(matched_by, query),
                response=response,
            )
            if response.sku:
                _write_cached_lookup(
                    settings=settings,
                    cache_key=_cache_key("sku", response.sku),
                    response=response,
                )
            if response.mpn:
                _write_cached_lookup(
                    settings=settings,
                    cache_key=_cache_key("mpn", response.mpn),
                    response=response,
                )
            return response

    return _empty_lookup()


def _lookup_by_product_number(
    settings: Settings,
    product_number: str,
    matched_by: str,
) -> LcscLookupResponse:
    payload = _call_lcsc_api(
        settings=settings,
        path=f"/rest/wmsc2agent/product/info/{product_number}",
        extra_params={},
    )
    return _normalize_lookup_response(
        raw_result=payload.get("result"),
        matched_by=matched_by,
        query=product_number,
    )


def _lookup_by_search(
    settings: Settings,
    keyword: str,
    matched_by: str,
) -> LcscLookupResponse:
    payload = _call_lcsc_api(
        settings=settings,
        path="/rest/wmsc2agent/search/product",
        extra_params={
            "keyword": keyword,
            "match_type": "exact",
            "current_page": "1",
            "page_size": "10",
            "is_available": "true",
        },
    )
    search_record = _select_best_record(
        records=_collect_product_records(payload.get("result")),
        matched_by=matched_by,
        query=keyword,
    )
    if search_record is None:
        return _empty_lookup()

    product_number = _first_text(
        search_record,
        ("product_number", "productNumber", "lcscPartNumber"),
    )
    if product_number:
        detail_response = _lookup_by_product_number(
            settings=settings,
            product_number=product_number,
            matched_by=matched_by,
        )
        if detail_response.found:
            return detail_response

    return _normalize_lookup_response(
        raw_result=search_record,
        matched_by=matched_by,
        query=keyword,
    )


def _call_lcsc_api(
    settings: Settings,
    path: str,
    extra_params: dict[str, str],
) -> dict[str, Any]:
    timestamp = str(int(time.time()))
    nonce = _build_nonce()
    signature = _build_signature(
        key=settings.lcsc_openapi_key,
        secret=settings.lcsc_openapi_secret,
        nonce=nonce,
        timestamp=timestamp,
    )
    query = {
        "key": settings.lcsc_openapi_key,
        "nonce": nonce,
        "timestamp": timestamp,
        "signature": signature,
        **extra_params,
    }
    url = f"{settings.lcsc_openapi_base_url}{path}?{urlencode(query)}"

    try:
        with urlopen(url, timeout=12) as response:
            status_code = getattr(response, "status", 200)
            body = response.read().decode("utf-8")
    except Exception as error:
        raise LookupRequestError(f"LCSC lookup request failed: {error}") from error

    try:
        payload = json.loads(body)
    except json.JSONDecodeError as error:
        raise LookupRequestError("LCSC lookup returned invalid JSON.") from error

    if status_code < 200 or status_code >= 300:
        raise LookupRequestError(
            f"LCSC lookup failed with HTTP {status_code}.",
        )

    if not payload.get("success", False):
        message = str(payload.get("message") or "LCSC lookup was rejected.")
        code = str(payload.get("code") or "unknown")
        raise LookupRequestError(f"LCSC lookup failed ({code}): {message}")

    return payload


def _normalize_lookup_response(
    raw_result: Any,
    matched_by: str,
    query: str,
) -> LcscLookupResponse:
    record = _select_best_record(
        records=_collect_product_records(raw_result),
        matched_by=matched_by,
        query=query,
    )
    if record is None:
        return _empty_lookup()

    sku = _first_text(record, ("product_number", "productNumber", "lcscPartNumber"))
    mpn = _first_text(
        record,
        ("product_model", "productModel", "mfrPartNumber", "mpn", "partNumber"),
    )
    name = _first_text(
        record,
        (
            "productIntroEn",
            "productIntro",
            "productDescEn",
            "productDesc",
            "productName",
            "name",
            "title",
        ),
    )
    package_name = _first_text(
        record,
        ("encapStandard", "encap_standard", "packageName", "package", "pkg"),
    )
    brand = _first_text(record, ("brandName", "brand_name", "manufacturerName"))
    category = _first_text(
        record,
        ("catalogName", "catalog_name", "categoryName", "category"),
    )
    category_path = _build_category_path(record)
    if category_path and not category:
        category = category_path.split("/")[-1].strip()
    official_url = None
    if sku:
        official_url = f"https://www.lcsc.com/product-detail/{sku}.html"

    confidence = "fallback"
    if matched_by == "sku" and sku and sku.casefold() == query.casefold():
        confidence = "exact"
    elif matched_by == "mpn" and mpn and mpn.casefold() == query.casefold():
        confidence = "exact"

    return LcscLookupResponse(
        found=bool(sku or mpn or name),
        sku=sku,
        name=name,
        mpn=mpn,
        package_name=package_name,
        category=category,
        category_path=category_path,
        brand=brand,
        official_url=official_url,
        matched_by=matched_by,
        confidence=confidence,
        cache_hit=False,
    )


def _collect_product_records(value: Any) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    if isinstance(value, dict):
        if _looks_like_product_record(value):
            records.append(value)
        for nested in value.values():
            records.extend(_collect_product_records(nested))
    elif isinstance(value, list):
        for item in value:
            records.extend(_collect_product_records(item))
    return records


def _select_best_record(
    records: Iterable[dict[str, Any]],
    matched_by: str,
    query: str,
) -> dict[str, Any] | None:
    normalized_query = query.casefold()
    exact_match: dict[str, Any] | None = None
    fuzzy_match: dict[str, Any] | None = None

    for record in records:
        sku = _first_text(record, ("product_number", "productNumber", "lcscPartNumber"))
        mpn = _first_text(
            record,
            ("product_model", "productModel", "mfrPartNumber", "mpn", "partNumber"),
        )
        name = _first_text(
            record,
            ("productIntroEn", "productIntro", "productName", "name", "title"),
        )

        if matched_by == "sku" and sku and sku.casefold() == normalized_query:
            exact_match = record
            break
        if matched_by == "mpn" and mpn and mpn.casefold() == normalized_query:
            exact_match = record
            break
        if matched_by == "name" and name and normalized_query in name.casefold():
            fuzzy_match = fuzzy_match or record
        if fuzzy_match is None:
            fuzzy_match = record

    return exact_match or fuzzy_match


def _looks_like_product_record(record: dict[str, Any]) -> bool:
    return any(key in record for key in _PRODUCT_KEYS)


def _first_text(record: dict[str, Any], keys: Iterable[str]) -> str | None:
    for key in keys:
        value = record.get(key)
        normalized = _normalize_text(_coerce_scalar(value))
        if normalized:
            return normalized

    for value in record.values():
        if isinstance(value, dict):
            nested = _first_text(value, keys)
            if nested:
                return nested
    return None


def _build_category_path(record: dict[str, Any]) -> str | None:
    for key in ("catalogPath", "categoryPath", "catalogNamePath"):
        value = _normalize_text(_coerce_scalar(record.get(key)))
        if value:
            return value

    chain_keys = (
        "grandCatalogName",
        "parentCatalogName",
        "catalogName",
        "childCatalogName",
        "categoryName",
    )
    segments = [
        segment
        for segment in (_normalize_text(_coerce_scalar(record.get(key))) for key in chain_keys)
        if segment
    ]
    if segments:
        deduped: list[str] = []
        for segment in segments:
            if segment not in deduped:
                deduped.append(segment)
        return " / ".join(deduped)
    return None


def _coerce_scalar(value: Any) -> str | None:
    if value is None:
        return None
    if isinstance(value, str):
        return value
    if isinstance(value, (int, float)):
        return str(value)
    return None


def _normalize_text(value: str | None) -> str | None:
    if value is None:
        return None
    normalized = value.strip()
    return normalized or None


def _validate_lookup_configuration(settings: Settings) -> None:
    if not settings.lcsc_openapi_key or not settings.lcsc_openapi_secret:
        raise LookupConfigurationError(
            "LCSC lookup is not configured on this server.",
        )


def _cache_key(kind: str, value: str) -> str:
    return f"{kind}:{value.casefold()}"


def _read_cached_lookup(
    settings: Settings,
    cache_key: str,
) -> LcscLookupResponse | None:
    now = time.time()
    with _LOOKUP_CACHE_LOCK:
        cached = _LOOKUP_CACHE.get(cache_key)
        if cached is None:
            return None
        expires_at, response = cached
        if expires_at <= now:
            _LOOKUP_CACHE.pop(cache_key, None)
            return None
        return response.model_copy(update={"cache_hit": True})


def _write_cached_lookup(
    settings: Settings,
    cache_key: str,
    response: LcscLookupResponse,
) -> None:
    ttl = max(settings.lcsc_lookup_cache_ttl_seconds, 0)
    if ttl <= 0 or not response.found:
        return
    with _LOOKUP_CACHE_LOCK:
        _LOOKUP_CACHE[cache_key] = (
            time.time() + ttl,
            response.model_copy(update={"cache_hit": False}),
        )


def _empty_lookup() -> LcscLookupResponse:
    return LcscLookupResponse(
        found=False,
        confidence="none",
        cache_hit=False,
    )


def _build_signature(
    key: str,
    secret: str,
    nonce: str,
    timestamp: str,
) -> str:
    raw = f"key={key}&nonce={nonce}&secret={secret}&timestamp={timestamp}"
    return sha1(raw.encode("utf-8")).hexdigest()


def _build_nonce() -> str:
    return f"{int(time.time() * 1000):x}"[-16:].rjust(16, "0")
