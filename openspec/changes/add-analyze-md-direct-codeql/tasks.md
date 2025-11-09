## 1. Implementation
- [x] 1.1 Add `--md-file` argument to argument parser in `Analyze.py`
- [x] 1.2 Create `run_md_direct_codeql()` async function for MD file processing
- [x] 1.3 Implement MD file content extraction logic
- [x] 1.4 Integrate with `tools/codeql_compose.py` CodeQLComposeTool
- [x] 1.5 Add MD file validation and error handling
- [x] 1.6 Update main() function to handle `--md-file` parameter
- [x] 1.7 Add parameter exclusivity validation (cannot use with --case, --list, --validate)
- [x] 1.8 Update help text and usage examples

## 2. Testing
- [x] 2.1 Test MD file reading with valid vulnerability description
- [x] 2.2 Test error handling for non-existent MD files
- [x] 2.3 Test parameter exclusivity validation
- [ ] 2.4 Test CodeQL generation from MD content
- [ ] 2.5 Test integration with existing model provider options

## 3. Documentation
- [x] 3.1 Update command-line help text
- [x] 3.2 Add usage examples to argument parser epilog
- [x] 3.3 Update inline comments for new functionality
