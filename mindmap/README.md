# 思维导图 API

一个用于管理思维导图的 Spring Boot 应用，尤其适用于测试用例组织。

## 特性
- 创建和管理具有富文本描述和备注的思维导图节点。
- 层级结构（节点可以有子节点）。
- 节点状态管理 (PENDING_TEST, TESTED, CANCELLED)，具有自动重新计算功能：
    - 设置节点状态会传播到其所有子节点。
    - 父节点状态会根据其子节点的集体状态进行更新。
- 按需求 ID 查询思维导图。
- 通过 Liquibase 初始化示例数据。

## 运行应用
1.  **数据库设置**:
    *   确保你有一个正在运行的 MySQL 实例。
    *   创建一个名为 `testCase` 的数据库。
    *   更新 `src/main/resources/application.properties` 文件，填入你的 MySQL 用户名和密码：
        ```properties
        spring.datasource.url=jdbc:mysql://localhost:3306/testCase?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
        spring.datasource.username=你的mysql用户名
        spring.datasource.password=你的mysql密码
        ```
2.  **构建**:
    ```bash
    ./mvnw clean package
    ```
3.  **运行**:
    ```bash
    java -jar target/mindmap-0.0.1-SNAPSHOT.jar
    ```
    应用将会启动，Liquibase 将应用数据库结构和示例数据。

## API 文档

所有 API 端点都以 `/api/mindmap` 为前缀。

---

### 1. 添加节点
*   **POST** `/api/mindmap/nodes`
*   **描述**: 创建一个新的思维导图节点。
*   **请求体**: `MindMapNode` 对象 (JSON)
    ```json
    {
        "parentId": null, // 或父节点的 ID
        "description": "新功能点子",
        "remarks": "关于此功能的详细说明。",
        "requirementId": "REQ-NEW-123",
        "backendDeveloper": "dev_be",
        "frontendDeveloper": "dev_fe",
        "tester": "tester_x",
        "requirementReference": "需求文档链接",
        "status": "PENDING_TEST" // 可选，默认为 PENDING_TEST
    }
    ```
*   **cURL 示例**:
    ```bash
    curl -X POST -H "Content-Type: application/json" -d '{
        "parentId": null,
        "description": "API 测试的根节点",
        "requirementId": "API-TEST-001"
    }' http://localhost:8080/api/mindmap/nodes
    ```
*   **成功响应 (201 CREATED)**:
    ```json
    {
        "id": 1, // 生成的 ID
        "parentId": null,
        "description": "API 测试的根节点",
        "remarks": null,
        "requirementId": "API-TEST-001",
        "backendDeveloper": null,
        "frontendDeveloper": null,
        "tester": null,
        "requirementReference": null,
        "status": "PENDING_TEST"
        // 通过 DTO 创建新节点时，children 列表将为空，但此端点返回的是实体
    }
    ```
*   **错误响应 (400 Bad Request)**: 如果描述缺失或其他验证失败。

---

### 2. 通过 ID 获取节点
*   **GET** `/api/mindmap/nodes/{nodeId}`
*   **描述**: 通过 ID 检索特定的思维导图节点。
*   **路径参数**: `nodeId` (Long)
*   **cURL 示例**:
    ```bash
    curl http://localhost:8080/api/mindmap/nodes/100 
    ```
    (假设 ID 为 100 的节点存在于示例数据中)
*   **成功响应 (200 OK)**:
    ```json
    {
        "id": 100,
        "parentId": null,
        "description": "用户认证模块",
        "remarks": "处理用户登录、注册和密码管理的所有方面。",
        "requirementId": "REQ-AUTH-001",
        // ... 其他字段 ...
        "status": "PENDING_TEST"
    }
    ```
*   **错误响应 (404 Not Found)**: 如果具有给定 ID 的节点不存在。

---

### 3. 通过需求 ID 获取思维导图 (树形结构)
*   **GET** `/api/mindmap/requirements/{requirementId}/nodes`
*   **描述**: 检索给定需求 ID 的整个思维导图 (以树形结构)。返回该需求的根节点列表，每个根节点都填充了其子节点。
*   **路径参数**: `requirementId` (String)
*   **cURL 示例**:
    ```bash
    curl http://localhost:8080/api/mindmap/requirements/REQ-AUTH-001/nodes
    ```
*   **成功响应 (200 OK)**:
    ```json
    [
        {
            "id": 100,
            "parentId": null,
            "description": "用户认证模块",
            // ... 其他字段 ...
            "status": "PENDING_TEST",
            "children": [
                {
                    "id": 101,
                    "parentId": 100,
                    "description": "用户注册",
                    // ... 其他字段 ...
                    "status": "PENDING_TEST",
                    "children": [
                        {
                            "id": 204,
                            // ...
                        },
                        {
                            "id": 205,
                            // ...
                        }
                    ]
                },
                {
                    "id": 102,
                    "parentId": 100,
                    "description": "用户登录",
                    // ... 其他字段 ...
                    "status": "PENDING_TEST",
                    "children": [
                        // ... 用户登录的子节点
                    ]
                }
                // ... 用户认证模块的其他子节点
            ]
        }
        // 如果一个需求可以有多个根节点，则可能还有其他根节点 (在此设置中不常见)
    ]
    ```
*   **成功响应 (200 OK，列表为空)**: 如果需求 ID 有效但没有节点。
    ```json
    []
    ```

---

### 4. 删除节点及其子节点
*   **DELETE** `/api/mindmap/nodes/{nodeId}/tree`
*   **描述**: 删除一个节点及其所有后代节点。
*   **路径参数**: `nodeId` (Long)
*   **cURL 示例**:
    ```bash
    curl -X DELETE http://localhost:8080/api/mindmap/nodes/203/tree 
    ```
    (假设节点 203 “使用不存在的用户名登录” 存在)
*   **成功响应 (204 No Content)**
*   **注意**: 请谨慎测试，因为这是一个破坏性操作。

---

### 5. 删除节点，保留子节点
*   **DELETE** `/api/mindmap/nodes/{nodeId}`
*   **描述**: 删除单个节点，并将其子节点重新指定给被删除节点的父节点。
*   **路径参数**: `nodeId` (Long)
*   **cURL 示例**:
    ```bash
    curl -X DELETE http://localhost:8080/api/mindmap/nodes/103 
    ```
    (假设节点 103 “密码重置” 存在且有子节点，或用于测试删除叶子节点)
*   **成功响应 (204 No Content)**

---

### 6. 更新节点描述
*   **PUT** `/api/mindmap/nodes/{nodeId}/description`
*   **描述**: 更新特定节点的描述。请求体应为新的描述字符串 (纯文本，但由应用逻辑解释为富文本)。
*   **路径参数**: `nodeId` (Long)
*   **请求体**: `String` (原始文本)
*   **cURL 示例**:
    ```bash
    curl -X PUT -H "Content-Type: text/plain" -d "节点 101 的更新描述" http://localhost:8080/api/mindmap/nodes/101/description
    ```
*   **成功响应 (200 OK)**:
    ```json
    {
        "id": 101,
        "description": "节点 101 的更新描述",
        // ... 其他字段保持不变 ...
    }
    ```
*   **错误响应 (404 Not Found)**: 如果节点不存在。
*   **错误响应 (400 Bad Request)**: 如果描述为空。

---

### 7. 更新节点备注
*   **PUT** `/api/mindmap/nodes/{nodeId}/remarks`
*   **描述**: 更新特定节点的备注。请求体应为新的备注字符串。
*   **路径参数**: `nodeId` (Long)
*   **请求体**: `String` (原始文本，可选)
*   **cURL 示例**:
    ```bash
    curl -X PUT -H "Content-Type: text/plain" -d "这些是新的备注。" http://localhost:8080/api/mindmap/nodes/101/remarks
    ```
*   **成功响应 (200 OK)**:
    ```json
    {
        "id": 101,
        "remarks": "这些是新的备注。",
        // ... 其他字段保持不变 ...
    }
    ```
*   **错误响应 (404 Not Found)**: 如果节点不存在。

---

### 8. 设置节点状态 (单个)
*   **PUT** `/api/mindmap/nodes/{nodeId}/status`
*   **描述**: 设置单个节点的状态。触发子节点 (向下) 和父节点 (向上) 的状态重新计算。
*   **路径参数**: `nodeId` (Long)
*   **请求体**: `NodeStatus` 枚举字符串 (例如："TESTED", "CANCELLED", "PENDING_TEST")
*   **cURL 示例**:
    ```bash
    curl -X PUT -H "Content-Type: application/json" -d '"TESTED"' http://localhost:8080/api/mindmap/nodes/201/status
    ```
    (节点 201 是 “使用有效凭据登录”)
*   **成功响应 (200 OK)**:
    ```json
    {
        "id": 201,
        "status": "TESTED",
        // ... 其他字段 ...
        // 注意：响应是直接设置其状态的节点。
        // 子节点和父节点的状态作为副作用被更新。
    }
    ```
*   **错误响应 (404 Not Found)**: 如果节点不存在。
*   **错误响应 (400 Bad Request)**: 如果状态字符串无效。

---

### 9. 批量设置节点状态
*   **PUT** `/api/mindmap/nodes/status/batch`
*   **描述**: 设置多个节点的状态。触发受影响节点的子节点和父节点的状态重新计算。
*   **请求体**: `BatchStatusUpdateRequest` 对象 (JSON)
    ```json
    {
        "nodeIds": [201, 202], // 节点 ID 列表
        "status": "CANCELLED"  // 这些节点的新状态
    }
    ```
*   **cURL 示例**:
    ```bash
    curl -X PUT -H "Content-Type: application/json" -d '{
        "nodeIds": [201, 202],
        "status": "CANCELLED"
    }' http://localhost:8080/api/mindmap/nodes/status/batch
    ```
*   **成功响应 (200 OK)**: 已处理的批次中的节点列表。
    ```json
    [
        {
            "id": 201,
            "status": "CANCELLED",
            // ...
        },
        {
            "id": 202,
            "status": "CANCELLED",
            // ...
        }
    ]
    ```
*   **错误响应 (400 Bad Request)**: 如果请求格式错误 (例如：缺少字段、状态无效)。

---

### 10. 批量创建节点 (层级结构)
*   **POST** `/api/mindmap/nodes/batch`
*   **描述**: 根据层级请求结构创建根思维导图节点及其所有后代节点。整个操作是事务性的；如果任何节点创建失败，所有更改都将回滚。
*   **请求体**: `BatchCreateNodeDto` 对象 (JSON)。有关字段详细信息，请参阅 `BatchCreateNodeDto.java` (description, requirementId 是必需的；status 默认为 PENDING_TEST)。
    ```json
    {
        "description": "Root Project Alpha",
        "requirementId": "REQ-ALPHA-001",
        "remarks": "Main project container",
        "backendDeveloper": "dev_lead_be",
        "frontendDeveloper": "dev_lead_fe",
        "tester": "qa_lead",
        "status": "PENDING_TEST",
        "children": [
            {
                "description": "Phase 1: Design",
                "requirementId": "REQ-ALPHA-001", // 通常整个树使用相同的 reqId
                "remarks": "Initial design documents",
                "children": [
                    {
                        "description": "API Specification",
                        "requirementId": "REQ-ALPHA-001"
                    },
                    {
                        "description": "Database Schema",
                        "requirementId": "REQ-ALPHA-001"
                    }
                ]
            },
            {
                "description": "Phase 2: Development",
                "requirementId": "REQ-ALPHA-001",
                "children": [
                    {
                        "description": "Backend Implementation",
                        "requirementId": "REQ-ALPHA-001"
                    },
                    {
                        "description": "Frontend Implementation",
                        "requirementId": "REQ-ALPHA-001"
                    }
                ]
            }
        ]
    }
    ```
*   **cURL 示例**:
    ```bash
    curl -X POST -H "Content-Type: application/json" -d '{
        "description": "Root Project Beta",
        "requirementId": "REQ-BETA-002",
        "children": [
            {"description": "Task 1", "requirementId": "REQ-BETA-002"}
        ]
    }' http://localhost:8080/api/mindmap/nodes/batch
    ```
*   **成功响应 (201 CREATED)**: 以嵌套结构返回创建的根节点及其所有子节点 (使用 `MindMapNodeDto` 格式)。
    ```json
    {
        "id": 123, // 示例 ID
        "parentId": null,
        "description": "Root Project Alpha",
        "remarks": "Main project container",
        "requirementId": "REQ-ALPHA-001",
        "backendDeveloper": "dev_lead_be",
        "frontendDeveloper": "dev_lead_fe",
        "tester": "qa_lead",
        "status": "PENDING_TEST",
        "children": [
            {
                "id": 124,
                "parentId": 123,
                "description": "Phase 1: Design",
                // ... 其他字段 ...
                "children": [
                    // ... 孙子节点 ...
                ]
            }
            // ... 其他子节点 ...
        ]
    }
    ```
*   **错误响应 (400 Bad Request)**: 如果请求验证失败 (例如，缺少 `description` 或 `requirementId`，或其他 DTO 验证规则)。
*   **错误响应 (500 Internal Server Error)**: 如果在创建过程中发生数据库错误 (事务将回滚)。

---

### 11. 通过 AI 生成测试用例
*   **POST** `/api/mindmap/generate-from-requirement`
*   **描述**: 通过 OpenAI GPT API 为给定需求生成思维导图测试用例树。根节点以需求 ID 和标题命名。第一级子节点是功能点，第二级子节点是源自这些功能点的测试场景。测试场景描述包括测试用例 ID、分组、前置条件、测试步骤和预期结果等详细信息，并格式化为 Markdown。场景的引用需求文本也会被填充。从 AI 获取数据并保存到数据库的整个操作在可能的情况下是事务性的 (数据库部分)。
*   **请求体**: `RequirementInputDto` 对象 (JSON)。有关字段详细信息，请参阅 `RequirementInputDto.java` (`requirementId`, `requirementTitle`, `originalRequirementText` 是必需的)。
    ```json
    {
        "requirementId": "REQ-USER-LOGIN-001",
        "requirementTitle": "用户登录功能",
        "originalRequirementText": "用户需要能够使用其注册的电子邮箱和密码登录系统。系统应验证凭据的有效性。如果凭据有效，用户将被重定向到其个人仪表板。如果凭据无效，应显示相应的错误消息。登录尝试次数不应超过五次，之后账户将被临时锁定。"
    }
    ```
*   **cURL 示例**:
    ```bash
    curl -X POST -H "Content-Type: application/json" -d '{
        "requirementId": "REQ-USER-LOGIN-001",
        "requirementTitle": "用户登录功能",
        "originalRequirementText": "用户需要能够使用其注册的电子邮箱和密码登录系统。系统应验证凭据的有效性。如果凭据有效，用户将被重定向到其个人仪表板。如果凭据无效，应显示相应的错误消息。登录尝试次数不应超过五次，之后账户将被临时锁定。"
    }' http://localhost:8080/api/mindmap/generate-from-requirement
    ```
*   **成功响应 (201 CREATED)**: 以嵌套结构返回创建的根思维导图节点及其所有 AI 生成的子节点 (功能点和场景) (使用 `MindMapNodeDto` 格式)。
    ```json
    {
        "id": 201, // Example ID
        "parentId": null,
        "description": "REQ-USER-LOGIN-001 用户登录功能",
        "requirementId": "REQ-USER-LOGIN-001",
        // ... other root node fields ...
        "children": [
            {
                "id": 202,
                "parentId": 201,
                "description": "用户凭据有效性验证", // Example functional point
                // ...
                "children": [
                    {
                        "id": 203,
                        "parentId": 202,
                        "description": "### Test Scenario: 用户凭据有效性验证\n**Test Case ID:** TC-LOGIN-001\n**Test Case Group:** ...", // Example scenario description (Markdown)
                        "requirementReference": "...系统应验证凭据的有效性...", // Example quoted text
                        // ...
                    }
                ]
            }
            // ... other functional points ...
        ]
    }
    ```
*   **错误响应 (400 Bad Request)**: 如果请求验证失败 (例如，缺少 `requirementId`, `requirementTitle`, 或 `originalRequirementText`)。
*   **错误响应 (500 Internal Server Error)**: 如果 OpenAI API 调用失败，如果无法解析来自 OpenAI 的响应，如果生成的数据不符合预期格式，或者在节点创建过程中发生数据库错误。

---

### 12. 移动节点 (及其子节点)
*   **PUT** `/api/mindmap/nodes/{nodeToMoveId}/move-to/{newParentNodeId}`
*   **功能描述**: 将指定的节点 (`nodeToMoveId`) 及其所有子节点，整体移动成为另一个指定节点 (`newParentNodeId`) 的子节点。此操作会更新 `nodeToMoveId` 的 `parentId` 为 `newParentNodeId` 的 ID。
*   **路径参数**:
    *   `nodeToMoveId` (Long): 需要移动的节点的ID。
    *   `newParentNodeId` (Long): 新的父节点的ID。
*   **请求体**: 无。
*   **cURL 示例**:
    ```bash
    curl -X PUT http://localhost:8080/api/mindmap/nodes/105/move-to/102
    ```
    (此示例表示将 ID 为 105 的节点移动到 ID 为 102 的节点下，成为其子节点)
*   **成功响应**:
    *   `204 No Content`: 如果节点移动成功，或者指定的节点 (`nodeToMoveId`) 原本就已经是目标父节点 (`newParentNodeId`) 的子节点 (此时不执行任何操作)。
*   **错误响应**:
    *   `400 Bad Request` (错误请求):
        *   如果 `nodeToMoveId` 与 `newParentNodeId` 相同 (节点不能移动到自身下)。
        *   如果 `newParentNodeId` 是 `nodeToMoveId` 的后代节点之一 (避免循环依赖)。
    *   `404 Not Found` (未找到):
        *   如果 `nodeToMoveId` 指定的节点不存在。
        *   如果 `newParentNodeId` 指定的目标父节点不存在。

---

## 节点更新API

### PUT /api/mindmap/nodes/{id}

更新指定ID的脑图节点。

**路径参数:**

*   `id` (Long): 必需，要更新的节点的ID。

**请求体 (JSON):**

`UpdateNodeRequest` 对象，包含以下可选字段：

*   `description` (String): 节点描述 (富文本)。
*   `remarks` (String): 备注 (富文本)。
*   `requirementId` (String): 需求ID。
*   `backendDeveloper` (String): 后端开发人员。
*   `frontendDeveloper` (String): 前端开发人员。
*   `tester` (String): 测试人员。
*   `requirementReference` (String): 需求参考 (富文本)。
*   `status` (Enum): 节点状态 (例如: `PENDING_TEST`, `TESTED`, `CANCELLED`)。
*   `isExpanded` (Boolean): 节点是否展开 (true/false)。
*   `hasStrikethrough` (Boolean): 节点内容是否显示删除线 (true/false)。

**请求示例:**

```json
{
  "description": "更新后的节点描述",
  "status": "TESTED",
  "isExpanded": false,
  "hasStrikethrough": true
}
```

**成功响应 (200 OK):**

返回更新后的 `MindMapNodeDto` 对象，结构与创建和查询节点时返回的节点对象类似，会包含所有节点属性，包括更新后的值。

**失败响应:**

*   `404 Not Found`: 如果具有指定 `id` 的节点未找到。
*   `400 Bad Request`: 如果请求体无效 (例如，字段类型不匹配)。
```
