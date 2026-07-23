FROM python:3.13-slim

ARG CODEQL_VERSION=2.23.3
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 PATH="/opt/codeql:/app/.venv/bin:${PATH}"

RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential ca-certificates curl git openjdk-17-jdk-headless unzip \
    && curl -fsSL "https://github.com/github/codeql-cli-binaries/releases/download/v${CODEQL_VERSION}/codeql-linux64.zip" -o /tmp/codeql.zip \
    && unzip -q /tmp/codeql.zip -d /opt \
    && rm /tmp/codeql.zip \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --gid 10001 app && useradd --uid 10001 --gid app --create-home app
WORKDIR /app
COPY --chown=app:app pyproject.toml uv.lock README.md Analyze.py ./
COPY --chown=app:app pure_auto_codeql ./pure_auto_codeql
COPY --chown=app:app migrations ./migrations
COPY --chown=app:app alembic.ini ./
RUN pip install --no-cache-dir uv && uv sync --frozen --no-dev

USER 10001:10001
EXPOSE 8000
CMD ["uvicorn", "pure_auto_codeql.api.server:app", "--host", "0.0.0.0", "--port", "8000"]
