from app.admin.data import _component_search_text, _normalize_search_term
from app.lcsc import _normalize_lookup_response, extract_product_parameters


def test_model_name_remains_separate_from_official_parameters():
    result = _normalize_lookup_response(
        {
            "productNumber": "C1",
            "productModel": "FRH0603B1002TS",
            "brandName": "FOJAN",
            "productIntro": "10kΩ ±1% 0.1W",
            "paramLinkedMap": {"阻值": "10kΩ", "精度": "±1%", "功率": "0.1W"},
        },
        "sku", "C1",
    )
    assert result.name == "FOJAN FRH0603B1002TS"
    assert result.description == "10kΩ ±1% 0.1W"
    assert result.parameters["阻值"] == "10kΩ"


def test_missing_parameters_are_not_inferred_from_model():
    assert extract_product_parameters({"productModel": "0603B104K500NT"}) == {}
    assert extract_product_parameters(None) == {}


def test_schema_product_properties_keep_explicit_values():
    result = extract_product_parameters({"additionalProperty": [
        {"name": "Capacitance", "value": "100nF"},
        {"name": "Voltage Rating", "value": "50V"},
    ]})
    assert result == {"Capacitance": "100nF", "Voltage Rating": "50V"}


def test_parameter_aliases_are_searchable_without_changing_name():
    index = _component_search_text(
        "FOJAN FRH0603B1002TS", "参数·Resistance：10kΩ\n参数：额定电压(V)：50",
    )
    for term in ("阻值10kohm", "resistance10kΩ", "耐压50V", "ratedvoltage50V"):
        assert _normalize_search_term(term) in index
    assert _normalize_search_term("耐压500V") not in index
