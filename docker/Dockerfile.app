FROM golang:1.26-bookworm AS lsp-mcp-builder

ARG MCP_LANGUAGE_SERVER_VERSION=v0.1.1
RUN go install "github.com/isaacphi/mcp-language-server@${MCP_LANGUAGE_SERVER_VERSION}"

FROM node:22-bookworm-slim AS node-runtime

FROM python:3.13-slim

ARG CODEQL_VERSION=2.26.1
ARG JDTLS_VERSION=1.60.0
ARG JDTLS_BUILD=202606262232
ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PATH="/opt/codeql:/app/.venv/bin:${PATH}"

RUN apt-get -o Acquire::Retries=5 update \
    && apt-get -o Acquire::Retries=5 install -y --no-install-recommends \
    build-essential ca-certificates clangd curl default-jdk-headless git unzip \
    && curl -fsSL "https://github.com/github/codeql-cli-binaries/releases/download/v${CODEQL_VERSION}/codeql-linux64.zip" -o /tmp/codeql.zip \
    && unzip -q /tmp/codeql.zip -d /opt \
    && rm /tmp/codeql.zip \
    && mkdir -p /opt/jdtls \
    && curl -fsSL "https://download.eclipse.org/jdtls/milestones/${JDTLS_VERSION}/jdt-language-server-${JDTLS_VERSION}-${JDTLS_BUILD}.tar.gz" -o /tmp/jdtls.tar.gz \
    && tar -xzf /tmp/jdtls.tar.gz -C /opt/jdtls \
    && ln -s /opt/jdtls/bin/jdtls /usr/local/bin/jdtls \
    && rm /tmp/jdtls.tar.gz \
    && rm -rf /var/lib/apt/lists/*

COPY --from=lsp-mcp-builder /go/bin/mcp-language-server /usr/local/bin/mcp-language-server
COPY --from=node-runtime /usr/local/bin/node /usr/local/bin/node
COPY --from=node-runtime /usr/local/lib/node_modules /usr/local/lib/node_modules
RUN ln -s /usr/local/lib/node_modules/npm/bin/npm-cli.js /usr/local/bin/npm \
    && ln -s /usr/local/lib/node_modules/npm/bin/npx-cli.js /usr/local/bin/npx

RUN groupadd --gid 10001 app && useradd --uid 10001 --gid app --create-home app
WORKDIR /app
COPY --chown=app:app pyproject.toml uv.lock README.md Analyze.py ./
COPY --chown=app:app config/keys.example.toml ./config/keys.example.toml
COPY --chown=app:app tools/mcp_runtime/package.json tools/mcp_runtime/package-lock.json ./tools/mcp_runtime/
COPY --chown=app:app tools/mcp_ripgrep/package.json tools/mcp_ripgrep/package-lock.json tools/mcp_ripgrep/tsconfig.json ./tools/mcp_ripgrep/
COPY --chown=app:app tools/mcp_ripgrep/src ./tools/mcp_ripgrep/src
COPY --chown=app:app pure_auto_codeql ./pure_auto_codeql
COPY --chown=app:app migrations ./migrations
COPY --chown=app:app alembic.ini ./
RUN pip install --no-cache-dir uv \
    && uv sync --frozen --no-dev \
    && npm --prefix tools/mcp_runtime ci --omit=dev \
    && npm --prefix tools/mcp_ripgrep ci \
    && npm --prefix tools/mcp_ripgrep run build \
    && npm --prefix tools/mcp_ripgrep prune --omit=dev

USER 10001:10001
EXPOSE 8000
CMD ["uvicorn", "pure_auto_codeql.api.server:app", "--host", "0.0.0.0", "--port", "8000"]
