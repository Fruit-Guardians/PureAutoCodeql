"""Compatibility helpers for MCP tool schemas."""

from __future__ import annotations

from typing import Iterable

from .logger import get_logger

logger = get_logger(__name__)


def _normalize_schema(schema):
    if not isinstance(schema, dict):
        return {"type": "object", "properties": {}}

    fixed = dict(schema)
    fixed.setdefault("type", "object")
    fixed.setdefault("properties", {})
    if not isinstance(fixed["properties"], dict):
        fixed["properties"] = {}
    return fixed


def fix_mcp_tools_schemas(tools: Iterable[object]) -> None:
    """Ensure tool argument schemas are valid OpenAI-compatible objects.

    Some MCP adapters can return schemas that only contain metadata keys. This
    keeps those tools usable by adding the minimal object schema fields.
    """

    for tool in tools:
        try:
            if hasattr(tool, "args_schema") and getattr(tool, "args_schema") is not None:
                schema_model = getattr(tool, "args_schema")
                if isinstance(schema_model, dict):
                    setattr(tool, "args_schema", _normalize_schema(schema_model))

            for attr in ("args", "input_schema", "schema"):
                if hasattr(tool, attr):
                    value = getattr(tool, attr)
                    if isinstance(value, dict):
                        setattr(tool, attr, _normalize_schema(value))
        except Exception as exc:
            logger.debug("Unable to normalize MCP tool schema for %r: %s", tool, exc)

