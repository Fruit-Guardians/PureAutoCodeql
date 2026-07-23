import json
import os
from datetime import UTC, datetime
from pathlib import Path

import pytest
from openai import AsyncOpenAI
from pydantic import BaseModel


class ContractOutput(BaseModel):
    language: str
    source_symbol: str
    sink_symbol: str
    confidence: str


@pytest.mark.real_llm
@pytest.mark.asyncio
async def test_real_llm_schema_and_budget():
    if os.getenv("RUN_REAL_LLM") != "1":
        pytest.skip("real LLM contract test is opt-in")
    if not os.getenv("OPENAI_API_KEY"):
        pytest.skip("OPENAI_API_KEY is not configured")

    model = os.getenv("REAL_LLM_MODEL", "gpt-5-mini")
    max_output_tokens = 300
    max_cost = float(os.getenv("REAL_LLM_MAX_USD", "0.05"))
    input_price = float(os.getenv("REAL_LLM_INPUT_USD_PER_MTOK", "5"))
    output_price = float(os.getenv("REAL_LLM_OUTPUT_USD_PER_MTOK", "30"))
    prompt = (
        "Return JSON only. Analyze this fixed Python snippet: "
        "`command = os.getenv('CMD'); os.system(command)`. "
        "Required keys: language, source_symbol, sink_symbol, confidence."
    )
    worst_case_cost = len(prompt.encode()) / 4 / 1_000_000 * input_price
    worst_case_cost += max_output_tokens / 1_000_000 * output_price
    if worst_case_cost > max_cost:
        pytest.fail(
            f"configured maximum ${max_cost:.4f} is below worst-case request ${worst_case_cost:.4f}"
        )

    response = await AsyncOpenAI().responses.create(
        model=model,
        input=prompt,
        max_output_tokens=max_output_tokens,
    )
    parsed = ContractOutput.model_validate_json(response.output_text)
    usage = response.usage
    actual_cost = usage.input_tokens / 1_000_000 * input_price
    actual_cost += usage.output_tokens / 1_000_000 * output_price
    assert actual_cost <= max_cost

    report = {
        "timestamp": datetime.now(UTC).isoformat(),
        "model": model,
        "input_tokens": usage.input_tokens,
        "output_tokens": usage.output_tokens,
        "estimated_cost_usd": actual_cost,
        "schema_valid": True,
        "output": parsed.model_dump(),
    }
    output = Path(os.getenv("REAL_LLM_REPORT", "outputs/evals/real-llm-contract.json"))
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2), encoding="utf-8")
