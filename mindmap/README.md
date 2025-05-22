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
