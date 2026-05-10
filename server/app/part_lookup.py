from __future__ import annotations

from dataclasses import dataclass
import json
from pathlib import Path
import re
import time
from typing import Any
from urllib.request import urlopen

from .config import Settings
from .lcsc import LookupConfigurationError, LookupRequestError, lookup_lcsc_product
from .schemas import LcscLookupResponse, PartLookupResponse, RecognitionRulesMetaResponse


class RecognitionRulesRefreshError(RuntimeError):
    pass


@dataclass(frozen=True)
class RecognitionValues:
    combined: str
    combined_lower: str
    sku: str | None
    mpn: str | None
    name: str | None
    brand: str | None
    package_hint: str | None
    source_type: str | None


@dataclass(frozen=True)
class PackagePattern:
    normalized: str
    compiled: re.Pattern[str]


@dataclass(frozen=True)
class ModelPattern:
    id: str
    match_type: str
    pattern: str
    vendor: str | None
    model_family: str | None
    package_name: str | None
    category: str | None
    confidence: str | None
    priority: int
    compiled: re.Pattern[str] | None

    def matches(self, values: RecognitionValues) -> bool:
        lowered_pattern = self.pattern.casefold()
        if self.match_type == "contains":
            return lowered_pattern in values.combined_lower
        if self.match_type == "prefix":
            return values.combined_lower.startswith(lowered_pattern) or (
                f" {lowered_pattern}" in values.combined_lower
            )
        return self.compiled is not None and bool(self.compiled.search(values.combined))


@dataclass(frozen=True)
class KeywordRule:
    id: str
    keywords: tuple[str, ...]
    vendor: str | None
    model_family: str | None
    category: str | None
    confidence: str | None
    priority: int

    def matches(self, values: RecognitionValues) -> bool:
        return any(keyword.casefold() in values.combined_lower for keyword in self.keywords)


@dataclass(frozen=True)
class RecognitionRuleSet:
    version: str
    updated_at: str | None
    source: str
    active_path: Path
    override_path: Path
    package_patterns: tuple[PackagePattern, ...]
    model_patterns: tuple[ModelPattern, ...]
    keyword_rules: tuple[KeywordRule, ...]


def lookup_part_metadata(
    settings: Settings,
    sku: str | None = None,
    mpn: str | None = None,
    name: str | None = None,
    brand: str | None = None,
    package_hint: str | None = None,
    source_type: str | None = None,
) -> PartLookupResponse:
    rules = _load_rules(settings)
    values = _build_values(
        sku=sku,
        mpn=mpn,
        name=name,
        brand=brand,
        package_hint=package_hint,
        source_type=source_type,
    )
    local_result = _lookup_local_rules(rules=rules, values=values)

    lcsc_result: LcscLookupResponse | None = None
    if any((values.sku, values.mpn, values.name)):
        try:
            lcsc_result = lookup_lcsc_product(
                settings=settings,
                sku=values.sku,
                mpn=values.mpn,
                name=values.name,
            )
        except (LookupConfigurationError, LookupRequestError):
            lcsc_result = None

    if lcsc_result is not None and lcsc_result.found:
        return _merge_lcsc_and_local(
            values=values,
            lcsc_result=lcsc_result,
            local_result=local_result,
        )

    if local_result is not None:
        return local_result

    return PartLookupResponse(
        found=False,
        source="none",
        confidence="none",
        rule_version=rules.version,
    )


def get_recognition_rules_meta(
    settings: Settings,
    refreshed: bool = False,
) -> RecognitionRulesMetaResponse:
    rules = _load_rules(settings)
    return RecognitionRulesMetaResponse(
        version=rules.version,
        updated_at=rules.updated_at,
        source=rules.source,
        active_path=str(rules.active_path),
        override_path=str(rules.override_path),
        remote_url=settings.import_rules_remote_url or None,
        web_fallback_enabled=settings.enable_web_fallback_resolvers,
        refreshed=refreshed,
    )


def refresh_recognition_rules(settings: Settings) -> RecognitionRulesMetaResponse:
    remote_url = settings.import_rules_remote_url.strip()
    if not remote_url:
        return get_recognition_rules_meta(settings, refreshed=False)

    override_path = _override_rules_path(settings)
    override_path.parent.mkdir(parents=True, exist_ok=True)
    try:
        with urlopen(remote_url, timeout=12) as response:
            payload = response.read().decode("utf-8")
    except Exception as error:  # pragma: no cover - network failures depend on host
        raise RecognitionRulesRefreshError(
            f"Recognition rules refresh failed: {error}",
        ) from error

    try:
        parsed = json.loads(payload)
    except json.JSONDecodeError as error:
        raise RecognitionRulesRefreshError(
            "Recognition rules refresh returned invalid JSON.",
        ) from error

    _build_rule_set(
        parsed,
        source="remote_cache",
        active_path=override_path,
        override_path=override_path,
    )
    override_path.write_text(
        json.dumps(parsed, ensure_ascii=True, indent=2) + "\n",
        encoding="utf-8",
    )
    return get_recognition_rules_meta(settings, refreshed=True)


def _build_values(
    sku: str | None,
    mpn: str | None,
    name: str | None,
    brand: str | None,
    package_hint: str | None,
    source_type: str | None,
) -> RecognitionValues:
    normalized_sku = _normalize_text(sku)
    normalized_mpn = _normalize_text(mpn)
    normalized_name = _normalize_text(name)
    normalized_brand = _normalize_text(brand)
    normalized_package_hint = _normalize_text(package_hint)
    normalized_source_type = _normalize_text(source_type)
    combined_parts = [
        value
        for value in (
            normalized_sku,
            normalized_mpn,
            normalized_name,
            normalized_brand,
            normalized_package_hint,
            normalized_source_type,
        )
        if value
    ]
    combined = " ".join(combined_parts)
    return RecognitionValues(
        combined=combined,
        combined_lower=combined.casefold(),
        sku=normalized_sku,
        mpn=normalized_mpn,
        name=normalized_name,
        brand=normalized_brand,
        package_hint=normalized_package_hint,
        source_type=normalized_source_type,
    )


def _lookup_local_rules(
    rules: RecognitionRuleSet,
    values: RecognitionValues,
) -> PartLookupResponse | None:
    normalized_package = _detect_package(rules, values)
    model_rule = next(
        (rule for rule in sorted(rules.model_patterns, key=lambda item: item.priority, reverse=True) if rule.matches(values)),
        None,
    )
    keyword_rule = next(
        (
            rule
            for rule in sorted(rules.keyword_rules, key=lambda item: item.priority, reverse=True)
            if (model_rule is None or rule.priority >= model_rule.priority) and rule.matches(values)
        ),
        None,
    )

    recognized_category = (
        model_rule.category
        if model_rule and model_rule.category
        else keyword_rule.category
        if keyword_rule and keyword_rule.category
        else None
    )
    recognized_package = (
        model_rule.package_name
        if model_rule and model_rule.package_name
        else normalized_package or values.package_hint
    )
    recognized_vendor = (
        values.brand
        or (model_rule.vendor if model_rule else None)
        or (keyword_rule.vendor if keyword_rule else None)
    )
    recognized_model_family = (
        (model_rule.model_family if model_rule else None)
        or (keyword_rule.model_family if keyword_rule else None)
    )
    confidence = (
        (model_rule.confidence if model_rule else None)
        or (keyword_rule.confidence if keyword_rule else None)
        or ("fallback" if recognized_package or recognized_category else None)
    )
    matched_by = (
        (model_rule.id if model_rule else None)
        or (keyword_rule.id if keyword_rule else None)
        or ("package_pattern" if normalized_package else None)
    )

    if not any((recognized_package, recognized_category, recognized_vendor, recognized_model_family)):
        return None

    category_path = recognized_category
    official_url = _build_lcsc_product_url(values.sku)
    return PartLookupResponse(
        found=True,
        source="local_rules",
        sku=values.sku,
        name=values.name,
        mpn=values.mpn,
        package_name=recognized_package,
        category=recognized_category,
        category_path=category_path,
        brand=values.brand,
        vendor=recognized_vendor,
        model_family=recognized_model_family,
        official_url=official_url,
        matched_by=matched_by,
        confidence=confidence or "fallback",
        cache_hit=False,
        rule_version=rules.version,
    )


def _detect_package(
    rules: RecognitionRuleSet,
    values: RecognitionValues,
) -> str | None:
    for pattern in rules.package_patterns:
        if pattern.compiled.search(values.combined):
            return pattern.normalized
    for token in _iter_compact_tokens(values):
        for pattern in rules.package_patterns:
            normalized_key = pattern.normalized.replace("-", "").casefold()
            if not normalized_key:
                continue
            if token == normalized_key:
                return pattern.normalized
            if token.startswith(normalized_key) and len(token) > len(normalized_key):
                return pattern.normalized
    return None


def _iter_compact_tokens(values: RecognitionValues) -> tuple[str, ...]:
    raw_values = (
        values.sku,
        values.mpn,
        values.name,
        values.brand,
        values.package_hint,
        values.source_type,
    )
    tokens: list[str] = []
    for raw_value in raw_values:
        if not raw_value:
            continue
        for token in re.split(r"[^A-Za-z0-9+-]+", raw_value):
            compact = token.replace("-", "").strip().casefold()
            if compact:
                tokens.append(compact)
    return tuple(tokens)


def _merge_lcsc_and_local(
    values: RecognitionValues,
    lcsc_result: LcscLookupResponse,
    local_result: PartLookupResponse | None,
) -> PartLookupResponse:
    source = lcsc_result.source
    if local_result is not None:
        source = f"{lcsc_result.source}+local_rules"

    return PartLookupResponse(
        found=True,
        source=source,
        sku=lcsc_result.sku or values.sku or (local_result.sku if local_result else None),
        name=lcsc_result.name or values.name or (local_result.name if local_result else None),
        mpn=lcsc_result.mpn or values.mpn or (local_result.mpn if local_result else None),
        package_name=lcsc_result.package_name or (
            local_result.package_name if local_result else values.package_hint
        ),
        category=lcsc_result.category or (local_result.category if local_result else None),
        category_path=lcsc_result.category_path or (
            local_result.category_path if local_result else None
        ),
        brand=lcsc_result.brand or values.brand or (local_result.brand if local_result else None),
        vendor=(
            local_result.vendor
            if local_result and local_result.vendor
            else lcsc_result.brand or values.brand
        ),
        model_family=local_result.model_family if local_result else None,
        official_url=lcsc_result.official_url or (
            local_result.official_url if local_result else None
        ),
        matched_by=lcsc_result.matched_by or (
            local_result.matched_by if local_result else None
        ),
        confidence=(
            lcsc_result.confidence
            if lcsc_result.confidence != "none"
            else local_result.confidence
            if local_result
            else "fallback"
        ),
        cache_hit=lcsc_result.cache_hit,
        rule_version=local_result.rule_version if local_result else None,
    )


def _load_rules(
    settings: Settings,
    allow_refresh: bool = True,
) -> RecognitionRuleSet:
    override_path = _override_rules_path(settings)
    if allow_refresh and _should_refresh_remote(settings, override_path):
        try:
            refresh_recognition_rules(settings)
        except RecognitionRulesRefreshError:
            pass

    if override_path.exists():
        payload = json.loads(override_path.read_text(encoding="utf-8"))
        return _build_rule_set(
            payload,
            source="remote_cache",
            active_path=override_path,
            override_path=override_path,
        )

    bundled_path = Path(__file__).with_name("recognition_rules.json")
    payload = json.loads(bundled_path.read_text(encoding="utf-8"))
    return _build_rule_set(
        payload,
        source="bundled",
        active_path=bundled_path,
        override_path=override_path,
    )


def _should_refresh_remote(
    settings: Settings,
    override_path: Path,
) -> bool:
    if not settings.import_rules_remote_url.strip():
        return False
    refresh_hours = max(settings.import_rules_refresh_hours, 1)
    if not override_path.exists():
        return True
    file_age_seconds = max(0.0, time.time() - override_path.stat().st_mtime)
    return file_age_seconds >= refresh_hours * 3600


def _build_rule_set(
    payload: dict[str, Any],
    source: str,
    active_path: Path,
    override_path: Path,
) -> RecognitionRuleSet:
    package_patterns = []
    for item in payload.get("package_patterns", []):
        if not isinstance(item, dict):
            continue
        normalized = _normalize_text(_coerce_text(item.get("normalized")))
        pattern = _normalize_text(_coerce_text(item.get("pattern")))
        if not normalized or not pattern:
            continue
        package_patterns.append(
            PackagePattern(
                normalized=normalized,
                compiled=re.compile(pattern, re.IGNORECASE),
            ),
        )

    model_patterns = []
    for index, item in enumerate(payload.get("model_patterns", [])):
        if not isinstance(item, dict):
            continue
        match_type = _normalize_text(_coerce_text(item.get("match_type"))) or "regex"
        pattern = _normalize_text(_coerce_text(item.get("pattern"))) or ""
        compiled = re.compile(pattern, re.IGNORECASE) if match_type == "regex" and pattern else None
        model_patterns.append(
            ModelPattern(
                id=_normalize_text(_coerce_text(item.get("id"))) or f"rule_{index}",
                match_type=match_type,
                pattern=pattern,
                vendor=_normalize_text(_coerce_text(item.get("vendor"))),
                model_family=_normalize_text(_coerce_text(item.get("model_family"))),
                package_name=_normalize_text(_coerce_text(item.get("package_name"))),
                category=_normalize_text(_coerce_text(item.get("category"))),
                confidence=_normalize_text(_coerce_text(item.get("confidence"))),
                priority=int(item.get("priority", 0) or 0),
                compiled=compiled,
            ),
        )

    keyword_rules = []
    for index, item in enumerate(payload.get("keyword_rules", [])):
        if not isinstance(item, dict):
            continue
        keywords = tuple(
            keyword
            for keyword in (
                _normalize_text(_coerce_text(raw_keyword))
                for raw_keyword in item.get("keywords", [])
            )
            if keyword
        )
        if not keywords:
            continue
        keyword_rules.append(
            KeywordRule(
                id=_normalize_text(_coerce_text(item.get("id"))) or f"keyword_{index}",
                keywords=keywords,
                vendor=_normalize_text(_coerce_text(item.get("vendor"))),
                model_family=_normalize_text(_coerce_text(item.get("model_family"))),
                category=_normalize_text(_coerce_text(item.get("category"))),
                confidence=_normalize_text(_coerce_text(item.get("confidence"))),
                priority=int(item.get("priority", 0) or 0),
            ),
        )

    return RecognitionRuleSet(
        version=_normalize_text(_coerce_text(payload.get("version"))) or "unknown",
        updated_at=_normalize_text(_coerce_text(payload.get("updated_at"))),
        source=source,
        active_path=active_path,
        override_path=override_path,
        package_patterns=tuple(package_patterns),
        model_patterns=tuple(model_patterns),
        keyword_rules=tuple(keyword_rules),
    )


def _override_rules_path(settings: Settings) -> Path:
    database_parent = Path(settings.database_path).expanduser().resolve().parent
    return database_parent / "recognition_rules_cache.json"


def _build_lcsc_product_url(sku: str | None) -> str | None:
    if not sku:
        return None
    if re.fullmatch(r"C\d+", sku, flags=re.IGNORECASE):
        return f"https://www.lcsc.com/product-detail/{sku.upper()}.html"
    return None


def _coerce_text(value: Any) -> str | None:
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
