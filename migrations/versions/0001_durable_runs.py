"""Create durable run platform tables."""

import sqlalchemy as sa
from alembic import op

revision = "0001_durable_runs"
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "runs",
        sa.Column("id", sa.String(64), primary_key=True),
        sa.Column("case_id", sa.String(255), nullable=False),
        sa.Column("status", sa.String(64), nullable=False),
        sa.Column("config", sa.JSON(), nullable=False),
        sa.Column("outcome", sa.String(64)),
        sa.Column("result", sa.JSON()),
        sa.Column("error", sa.JSON()),
        sa.Column("lease_owner", sa.String(255)),
        sa.Column("lease_expires_at", sa.DateTime(timezone=True)),
        sa.Column("heartbeat_at", sa.DateTime(timezone=True)),
        sa.Column("attempt_count", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("started_at", sa.DateTime(timezone=True)),
        sa.Column("completed_at", sa.DateTime(timezone=True)),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_index("ix_runs_case_id", "runs", ["case_id"])
    op.create_index("ix_runs_status", "runs", ["status"])
    op.create_table(
        "step_attempts",
        sa.Column("id", sa.BigInteger(), primary_key=True, autoincrement=True),
        sa.Column("run_id", sa.String(64), sa.ForeignKey("runs.id", ondelete="CASCADE"), nullable=False),
        sa.Column("step", sa.String(128), nullable=False),
        sa.Column("attempt", sa.Integer(), nullable=False),
        sa.Column("status", sa.String(64), nullable=False),
        sa.Column("diagnostics", sa.JSON()),
        sa.Column("started_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("completed_at", sa.DateTime(timezone=True)),
    )
    op.create_index("ix_step_attempts_run_id", "step_attempts", ["run_id"])
    op.create_index("ix_step_attempt_unique", "step_attempts", ["run_id", "step", "attempt"], unique=True)
    op.create_table(
        "artifacts",
        sa.Column("id", sa.BigInteger(), primary_key=True, autoincrement=True),
        sa.Column("run_id", sa.String(64), sa.ForeignKey("runs.id", ondelete="CASCADE"), nullable=False),
        sa.Column("name", sa.String(255), nullable=False),
        sa.Column("location", sa.Text(), nullable=False),
        sa.Column("media_type", sa.String(255), nullable=False),
        sa.Column("sha256", sa.String(64), nullable=False),
        sa.Column("size", sa.BigInteger(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_index("ix_artifacts_run_id", "artifacts", ["run_id"])
    op.create_index("ix_artifact_run_hash", "artifacts", ["run_id", "sha256"], unique=True)
    op.create_table(
        "event_checkpoints",
        sa.Column("run_id", sa.String(64), sa.ForeignKey("runs.id", ondelete="CASCADE"), primary_key=True),
        sa.Column("event_id", sa.String(64), nullable=False),
        sa.Column("terminal", sa.Boolean(), nullable=False, server_default=sa.false()),
        sa.Column("event", sa.JSON(), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_table(
        "cancellation_requests",
        sa.Column("run_id", sa.String(64), sa.ForeignKey("runs.id", ondelete="CASCADE"), primary_key=True),
        sa.Column("requested_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("requested_by", sa.String(255)),
        sa.Column("reason", sa.Text()),
        sa.Column("acknowledged_at", sa.DateTime(timezone=True)),
    )


def downgrade() -> None:
    op.drop_table("cancellation_requests")
    op.drop_table("event_checkpoints")
    op.drop_index("ix_artifact_run_hash", table_name="artifacts")
    op.drop_index("ix_artifacts_run_id", table_name="artifacts")
    op.drop_table("artifacts")
    op.drop_index("ix_step_attempt_unique", table_name="step_attempts")
    op.drop_index("ix_step_attempts_run_id", table_name="step_attempts")
    op.drop_table("step_attempts")
    op.drop_index("ix_runs_status", table_name="runs")
    op.drop_index("ix_runs_case_id", table_name="runs")
    op.drop_table("runs")
