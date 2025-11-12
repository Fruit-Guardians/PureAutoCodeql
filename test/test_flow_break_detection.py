import textwrap

from services.flow_break_detection import (
    FlowBreakClauseGenerator,
    FlowBreakDetectionConfig,
    FlowBreakQueryMerger,
    FlowQueryComponents,
    FlowQueryExtractor,
    FlowDetectionSkeletonBuilder,
    FlowBreakCandidate,
    GeneratedFlowClause,
)


def _build_sample_components() -> FlowQueryComponents:
    extractor = FlowQueryExtractor()
    query = textwrap.dedent(
        """
        import code.java
        import semmle.code.java.dataflow.ExternalFlow

        predicate isSource(DataFlow::Node src) {
          src instanceof FooSource
        }

        predicate isSink(DataFlow::Node sink) {
          sink instanceof BarSink
        }

        predicate isAdditionalFlowStep(DataFlow::Node src, DataFlow::Node dst) {
          none()
        }

        module Flow = TaintTracking::Global<MyConfig>;
        """
    ).strip()
    return extractor.extract(query)


def test_extract_query_components():
    components = _build_sample_components()
    assert "FooSource" in components.source_body
    assert "BarSink" in components.sink_body
    assert components.addition_body.strip() == "none()"


def test_skeleton_builder_replaces_placeholders():
    components = _build_sample_components()
    builder = FlowDetectionSkeletonBuilder()
    detection_query = builder.build(components, language="java")
    assert "<SOURCE>" not in detection_query
    assert "<SINK>" not in detection_query
    assert "<ISADDITION>" not in detection_query
    assert "FixedSourceNode" in detection_query


def test_clause_generator_respects_limits():
    config = FlowBreakDetectionConfig(max_clauses_per_round=2, max_total_clauses=5)
    generator = FlowBreakClauseGenerator(config)
    candidates = [
        FlowBreakCandidate(
            file="src/Foo.java",
            start_line=i,
            start_column=10,
            end_line=i,
            end_column=15,
            classification="assignment",
        )
        for i in range(1, 6)
    ]
    clauses = generator.generate_clauses(candidates, max_count=2, existing_keys=[])
    assert len(clauses) == 2
    keys = {clause.key for clause in clauses}
    assert len(keys) == 2


def test_merger_appends_clauses():
    extractor = FlowQueryExtractor()
    merger = FlowBreakQueryMerger(extractor)
    components = _build_sample_components()

    original_query = textwrap.dedent(
        """
        import code.java
        import semmle.code.java.dataflow.ExternalFlow

        predicate isSource(DataFlow::Node src) {
          src instanceof FooSource
        }

        predicate isSink(DataFlow::Node sink) {
          sink instanceof BarSink
        }

        predicate isAdditionalFlowStep(DataFlow::Node src, DataFlow::Node dst) {
          none()
        }

        module Flow = TaintTracking::Global<MyConfig>;
        """
    ).strip()

    clause = GeneratedFlowClause(
        clause="exists(DataFlow::Node s, DataFlow::Node t | s != t | src = s and dst = t)",
        key=("src/Foo.java", 10, 1, "assignment"),
        candidate=FlowBreakCandidate(
            file="src/Foo.java",
            start_line=10,
            start_column=1,
            end_line=10,
            end_column=10,
        ),
    )

    merged = merger.merge(original_query, components, [clause])
    assert "FlowBreakSupport" in merged
    assert "src = s and dst = t" in merged

