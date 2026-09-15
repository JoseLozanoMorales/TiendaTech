"""Regression tests: equal route counts must not hide a contract mismatch."""
import copy
import unittest

from generate import assert_coverage, consolidate, enrich, exposed, schema_30


class ContractValidationTest(unittest.TestCase):
    def test_replaced_route_with_same_count_fails(self):
        with self.assertRaisesRegex(ValueError, "Route mismatch"):
            assert_coverage({"paths": {"/wrong": {"get": {}}}}, {"GET /actual": {}})

    def test_second_method_on_same_path_is_required(self):
        with self.assertRaisesRegex(ValueError, "POST /items"):
            assert_coverage({"paths": {"/items": {"patch": {}}}},
                            {"PATCH /items": {}, "POST /items": {}})

    def test_request_body_cannot_disappear(self):
        with self.assertRaisesRegex(ValueError, "Missing request schema"):
            assert_coverage({"paths": {"/items": {"post": {}}}},
                            {"POST /items": {"requestBody": True}})

    def test_empty_media_type_cannot_replace_a_request_schema(self):
        with self.assertRaisesRegex(ValueError, "Missing request schema"):
            assert_coverage({"paths": {"/items": {"post": {"requestBody": {
                "content": {"application/json": {}}}}}}}, {"POST /items": {"requestBody": True}})

    def test_gateway_matches_base_and_descendants(self):
        self.assertTrue(exposed("/api/items", ["/api/items/**"]))
        self.assertTrue(exposed("/api/items/{id}", ["/api/items/**"]))
        self.assertFalse(exposed("/api/items-other", ["/api/items/**"]))

    def test_gateway_excludes_internal_paths_and_preserves_refs(self):
        result = consolidate({"example": {"paths": {
            "/api/items/{id}": {"get": {"x-gateway-exposed": True}},
            "/internal/probe": {"post": {"x-gateway-exposed": False}}}}})
        self.assertEqual(result["paths"], {"/api/items/{id}": {
            "$ref": "./example.yaml#/paths/~1api~1items~1{id}"}})

    def test_schema_null_union_and_dynamic_map(self):
        self.assertEqual(schema_30({"anyOf": [{"type": "integer"}, {"type": "null"}]}),
                         {"type": "integer", "nullable": True})
        self.assertEqual(schema_30({"type": "object", "additionalProperties": {"type": "object"}}),
                         {"type": "object", "additionalProperties": True})

    def test_nullable_reference_keeps_null_as_an_explicit_alternative(self):
        reference = {"$ref": "#/components/schemas/Example"}
        result = schema_30({"anyOf": [reference, {"type": "null"}]})
        self.assertEqual(result, {"anyOf": [reference, {
            "type": "object", "nullable": True, "enum": [None]}]})

    def test_envelope_does_not_wrap_binary_or_no_content(self):
        raw = {"paths": {
            "/api/items": {"get": {"responses": {"200": {"description": "OK", "content": {
                "*/*": {"schema": {"type": "array", "items": {"type": "string"}}}}}}}},
            "/api/items/{id}": {"delete": {"responses": {"200": {"description": "OK"}}}},
            "/api/image": {"get": {"responses": {"200": {"description": "OK", "content": {
                "*/*": {"schema": {"type": "string", "format": "binary"}}}}}}}}}
        result = enrich("example", copy.deepcopy(raw),
                        {"GET /api/items": {}, "DELETE /api/items/{id}": {}, "GET /api/image": {}},
                        ["/api/**"], {"example": {"DELETE /api/items/{id}": {"status": 204}}})
        data = result["paths"]["/api/items"]["get"]["responses"]["200"]["content"]["application/json"]
        self.assertEqual(data["schema"]["properties"]["data"]["type"], "array")
        binary = result["paths"]["/api/image"]["get"]["responses"]["200"]["content"]["*/*"]
        self.assertEqual(binary["schema"], {"type": "string", "format": "binary"})
        self.assertNotIn("content", result["paths"]["/api/items/{id}"]["delete"]["responses"]["204"])


if __name__ == "__main__":
    unittest.main()
