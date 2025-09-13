# Implementation Session Guide

## For Starting Implementation Sessions

When beginning work on the .jdpignore implementation with any model:

### 1. Read the Plan
```
Read rfc/jdpignore-implementation-plan.md to understand:
- Overall project structure
- Current phase and progress
- Specific implementation requirements
- Success criteria
```

### 2. Check Current Status
```
Look at the Status Tracking section to see:
- Which phases are completed
- What's currently in progress
- Any implementation notes from previous sessions
```

### 3. Set Up TodoWrite
```
Create todos for the current phase/task using TodoWrite tool
Track specific implementation steps within each phase
```

### 4. Update Progress
```
As you complete work:
- Update checkboxes in the plan document
- Add notes about decisions or issues encountered
- Mark todos as completed in real-time
```

### 5. Document Changes
```
If you need to deviate from the plan:
- Document the reason in Implementation Notes
- Update the plan if the change is significant
- Ensure future sessions understand the context
```

## Quick Start Commands

```bash
# Read the main plan
Read rfc/jdpignore-implementation-plan.md

# Check project structure
LS /Users/shannah/projects/jdeploy/cli/src/main/java/ca/weblite/jdeploy/services
LS /Users/shannah/projects/jdeploy/shared/src/main/java/ca/weblite/jdeploy/models

# Find existing related code
Grep "nativeNamespaces" /Users/shannah/projects/jdeploy --glob "**/*.java" --output_mode files_with_matches
```