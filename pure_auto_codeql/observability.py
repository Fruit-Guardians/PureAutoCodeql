"""OpenTelemetry setup and low-cardinality analysis instruments."""

from __future__ import annotations

import os
from contextlib import contextmanager
from typing import Iterator

from opentelemetry import metrics, trace
from opentelemetry.exporter.otlp.proto.http.metric_exporter import OTLPMetricExporter
from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter
from opentelemetry.sdk.metrics import MeterProvider
from opentelemetry.sdk.metrics.export import PeriodicExportingMetricReader
from opentelemetry.sdk.resources import Resource
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor

_configured = False


def configure_telemetry(service_name: str = "pure-auto-codeql") -> None:
    global _configured
    if _configured:
        return
    resource = Resource.create({"service.name": service_name})
    if os.getenv("OTEL_EXPORTER_OTLP_ENDPOINT"):
        tracer_provider = TracerProvider(resource=resource)
        tracer_provider.add_span_processor(BatchSpanProcessor(OTLPSpanExporter()))
        trace.set_tracer_provider(tracer_provider)
        reader = PeriodicExportingMetricReader(OTLPMetricExporter())
        metrics.set_meter_provider(MeterProvider(resource=resource, metric_readers=[reader]))
    _configured = True


tracer = trace.get_tracer("pure_auto_codeql.analysis")
meter = metrics.get_meter("pure_auto_codeql.analysis")
step_duration = meter.create_histogram("analysis.step.duration", unit="s")
llm_calls = meter.create_counter("analysis.llm.calls")
llm_tokens = meter.create_counter("analysis.llm.tokens")
tool_timeouts = meter.create_counter("analysis.tool.timeouts")
codeql_repairs = meter.create_counter("analysis.codeql.repairs")
path_count = meter.create_histogram("analysis.paths", unit="{path}")
queue_wait = meter.create_histogram("analysis.queue.wait", unit="s")
run_recoveries = meter.create_counter("analysis.run.recoveries")


@contextmanager
def step_span(run_id: str, step: str) -> Iterator[trace.Span]:
    with tracer.start_as_current_span(
        f"analysis.{step}",
        attributes={"analysis.run_id": run_id, "analysis.step": step},
    ) as span:
        yield span
