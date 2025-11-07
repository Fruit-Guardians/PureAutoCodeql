## Context

### Problem Statement
The current HTTP API system uses globally configured LLM providers through environment variables. All analysis tasks inherit the same LLM configuration, which limits flexibility in production scenarios where different tasks may require:

1. Different model capabilities (reasoning vs chat)
2. Cost optimization across providers
3. Provider availability redundancy
4. Task-specific model selection

### Current Architecture
- LLM configuration is read from environment variables at startup
- All tasks share the same `THINK_CONFIG` and `CHAT_CONFIG`
- No runtime provider selection capability
- Fixed provider fallback logic in `config.py`

### Stakeholders
- **API Users**: Need to specify LLM providers per task
- **System Administrators**: Need to monitor provider availability
- **Developers**: Need to maintain backward compatibility

## Goals / Non-Goals

### Goals
- Enable per-task LLM provider and model selection
- Provide provider availability status API
- Maintain backward compatibility with existing behavior
- Support all existing LLM providers (DeepSeek, SiliconFlow, Zhipu)
- Enable provider switching without service restart

### Non-Goals
- Dynamic model loading during runtime
- Provider load balancing
- Custom provider configuration (beyond existing providers)
- Real-time provider performance monitoring

## Decisions

### Decision 1: Task-level LLM Configuration
**What**: Extend `AnalysisRequest` to include optional LLM provider configuration
**Why**: Allows fine-grained control per analysis task while maintaining defaults
**Trade-off**: Increased request complexity vs flexibility gain

```python
class LLMProviderConfig(BaseModel):
    provider: Optional[LLMProvider] = None
    think_model: Optional[str] = None
    chat_model: Optional[str] = None
    api_key: Optional[str] = None  # Override default
    base_url: Optional[str] = None  # Override default
    temperature: Optional[float] = None
    max_tokens: Optional[int] = None
```

### Decision 2: Provider Status API
**What**: Add dedicated endpoints for LLM provider information
**Why**: Enables clients to make informed provider selection decisions
**Alternative**: Could embed in existing endpoints, but separate concerns are cleaner

### Decision 3: Configuration Layer Separation
**What**: Keep existing `config.py` for defaults, add task-specific config handling
**Why**: Preserves backward compatibility and clear separation of concerns
**Implementation**: Task configs override defaults when specified

### Decision 4: Validation Strategy
**What**: Validate provider availability at task creation time
**Why**: Prevents task failures due to unreachable providers
**Trade-off**: Slower task creation vs better user experience

## Risks / Trade-offs

### Risk 1: Configuration Complexity
**Risk**: Users may find the expanded configuration options overwhelming
**Mitigation**: 
- Keep all LLM fields optional with sensible defaults
- Provide clear documentation and examples
- Implement validation helpers

### Risk 2: Security Concerns
**Risk**: API keys passed in requests could be exposed
**Mitigation**:
- Never log API keys
- Mark API key fields as sensitive in API documentation
- Consider using secure storage for frequently used keys

### Risk 3: Provider Rate Limiting
**Risk**: Dynamic provider switching could trigger rate limits
**Mitigation**:
- Monitor provider status before switching
- Implement rate limiting awareness in configuration
- Provide provider health status information

### Trade-off 1: Performance vs Flexibility
**Trade-off**: Additional validation steps increase task creation latency
**Justification**: Better to fail fast with clear error messages than have tasks fail mid-execution

## Migration Plan

### Phase 1: Core Infrastructure
1. Extend data models with new LLM configuration fields
2. Update config system to handle task-level overrides
3. Implement provider status checking utilities

### Phase 2: API Implementation
1. Add LLM provider endpoints
2. Update analysis endpoints to handle new configuration
3. Implement validation middleware

### Phase 3: Integration
1. Update task manager to use custom LLM configs
2. Modify analysis workflows to accept custom configs
3. Add comprehensive error handling

### Phase 4: Testing and Documentation
1. Comprehensive testing of all provider combinations
2. Update API documentation
3. Create migration guide for existing users

## Open Questions

1. **API Key Management**: Should we support API key aliases/references for frequently used keys?
2. **Provider Persistence**: Should frequently used provider configurations be stored?
3. **Model Discovery**: Should we add API to discover available models per provider?
4. **Configuration Templates**: Should we provide predefined configuration templates for common use cases?

## Implementation Notes

### Backward Compatibility
- All new fields are optional
- Existing requests continue to work with default configuration
- Default behavior unchanged when no LLM config provided

### Error Handling Strategy
- Validate provider existence before task creation
- Return clear, actionable error messages
- Provider connectivity failures result in 503 status
- Invalid configurations result in 422 status

### Monitoring Considerations
- Log provider usage per task (without sensitive data)
- Track provider success/failure rates
- Monitor task completion times by provider