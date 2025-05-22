# Mind Map API

A Spring Boot application for managing mind maps, particularly for test case organization.

## Features
- Create and manage mind map nodes with rich text descriptions and remarks.
- Hierarchical structure (nodes can have children).
- Status management for nodes (PENDING_TEST, TESTED, CANCELLED) with automatic recalculation:
    - Setting a node's status propagates to all its children.
    - Parent node status updates based on the collective status of its children.
- Query mind maps by requirement ID.
- Sample data initialized via Liquibase.

## Running the Application
1.  **Database Setup**:
    *   Ensure you have a MySQL instance running.
    *   Create a database named `testCase`.
    *   Update `src/main/resources/application.properties` with your MySQL username and password:
        ```properties
        spring.datasource.url=jdbc:mysql://localhost:3306/testCase?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
        spring.datasource.username=your_mysql_username
        spring.datasource.password=your_mysql_password
        ```
2.  **Build**:
    ```bash
    ./mvnw clean package
    ```
3.  **Run**:
    ```bash
    java -jar target/mindmap-0.0.1-SNAPSHOT.jar
    ```
    The application will start, and Liquibase will apply the schema and sample data.

## API Documentation

All API endpoints are prefixed with `/api/mindmap`.

---

### 1. Add Node
*   **POST** `/api/mindmap/nodes`
*   **Description**: Creates a new mind map node.
*   **Request Body**: `MindMapNode` object (JSON)
    ```json
    {
        "parentId": null, // or ID of parent node
        "description": "New Feature Idea",
        "remarks": "Detailed notes about this feature.",
        "requirementId": "REQ-NEW-123",
        "backendDeveloper": "dev_be",
        "frontendDeveloper": "dev_fe",
        "tester": "tester_x",
        "requirementReference": "Link to spec",
        "status": "PENDING_TEST" // Optional, defaults to PENDING_TEST
    }
    ```
*   **cURL Example**:
    ```bash
    curl -X POST -H "Content-Type: application/json" -d '{
        "parentId": null,
        "description": "Root Node for API Test",
        "requirementId": "API-TEST-001"
    }' http://localhost:8080/api/mindmap/nodes
    ```
*   **Success Response (201 CREATED)**:
    ```json
    {
        "id": 1, // Generated ID
        "parentId": null,
        "description": "Root Node for API Test",
        "remarks": null,
        "requirementId": "API-TEST-001",
        "backendDeveloper": null,
        "frontendDeveloper": null,
        "tester": null,
        "requirementReference": null,
        "status": "PENDING_TEST"
        // children list will be empty for a new node via DTO, but this endpoint returns the entity
    }
    ```
*   **Error Response (400 Bad Request)**: If description is missing or other validation fails.

---

### 2. Get Node by ID
*   **GET** `/api/mindmap/nodes/{nodeId}`
*   **Description**: Retrieves a specific mind map node by its ID.
*   **Path Parameter**: `nodeId` (Long)
*   **cURL Example**:
    ```bash
    curl http://localhost:8080/api/mindmap/nodes/100 
    ```
    (Assuming node with ID 100 exists from sample data)
*   **Success Response (200 OK)**:
    ```json
    {
        "id": 100,
        "parentId": null,
        "description": "User Authentication Module",
        "remarks": "Handles all aspects of user login, registration, and password management.",
        "requirementId": "REQ-AUTH-001",
        // ... other fields ...
        "status": "PENDING_TEST"
    }
    ```
*   **Error Response (404 Not Found)**: If node with the given ID doesn't exist.

---

### 3. Get Mind Map by Requirement ID (Tree Structure)
*   **GET** `/api/mindmap/requirements/{requirementId}/nodes`
*   **Description**: Retrieves the entire mind map (as a tree) for a given requirement ID. Returns a list of root nodes for that requirement, each populated with its children.
*   **Path Parameter**: `requirementId` (String)
*   **cURL Example**:
    ```bash
    curl http://localhost:8080/api/mindmap/requirements/REQ-AUTH-001/nodes
    ```
*   **Success Response (200 OK)**:
    ```json
    [
        {
            "id": 100,
            "parentId": null,
            "description": "User Authentication Module",
            // ... other fields ...
            "status": "PENDING_TEST",
            "children": [
                {
                    "id": 101,
                    "parentId": 100,
                    "description": "User Registration",
                    // ... other fields ...
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
                    "description": "User Login",
                    // ... other fields ...
                    "status": "PENDING_TEST",
                    "children": [
                        // ... children of User Login
                    ]
                }
                // ... other children of User Authentication Module
            ]
        }
        // Potentially other root nodes if a requirement can have multiple roots (not typical for this setup)
    ]
    ```
*   **Success Response (200 OK with Empty List)**: If requirement ID is valid but has no nodes.
    ```json
    []
    ```

---

### 4. Delete Node and Children
*   **DELETE** `/api/mindmap/nodes/{nodeId}/tree`
*   **Description**: Deletes a node and all its descendants.
*   **Path Parameter**: `nodeId` (Long)
*   **cURL Example**:
    ```bash
    curl -X DELETE http://localhost:8080/api/mindmap/nodes/203/tree 
    ```
    (Assuming node 203 "Login with non-existent username" exists)
*   **Success Response (204 No Content)**
*   **Note**: Test with caution, as this is a destructive operation.

---

### 5. Delete Node, Keep Children
*   **DELETE** `/api/mindmap/nodes/{nodeId}`
*   **Description**: Deletes a single node and re-parents its children to the deleted node's parent.
*   **Path Parameter**: `nodeId` (Long)
*   **cURL Example**:
    ```bash
    curl -X DELETE http://localhost:8080/api/mindmap/nodes/103 
    ```
    (Assuming node 103 "Password Reset" exists and has children, or to test deletion of a leaf node)
*   **Success Response (204 No Content)**

---

### 6. Update Node Description
*   **PUT** `/api/mindmap/nodes/{nodeId}/description`
*   **Description**: Updates the description of a specific node. The request body should be the new description string (plain text, but interpreted as rich text by the application logic).
*   **Path Parameter**: `nodeId` (Long)
*   **Request Body**: `String` (raw text)
*   **cURL Example**:
    ```bash
    curl -X PUT -H "Content-Type: text/plain" -d "Updated description for node 101" http://localhost:8080/api/mindmap/nodes/101/description
    ```
*   **Success Response (200 OK)**:
    ```json
    {
        "id": 101,
        "description": "Updated description for node 101",
        // ... other fields remain ...
    }
    ```
*   **Error Response (404 Not Found)**: If node doesn't exist.
*   **Error Response (400 Bad Request)**: If description is empty.

---

### 7. Update Node Remarks
*   **PUT** `/api/mindmap/nodes/{nodeId}/remarks`
*   **Description**: Updates the remarks of a specific node. The request body should be the new remarks string.
*   **Path Parameter**: `nodeId` (Long)
*   **Request Body**: `String` (raw text, optional)
*   **cURL Example**:
    ```bash
    curl -X PUT -H "Content-Type: text/plain" -d "These are new remarks." http://localhost:8080/api/mindmap/nodes/101/remarks
    ```
*   **Success Response (200 OK)**:
    ```json
    {
        "id": 101,
        "remarks": "These are new remarks.",
        // ... other fields remain ...
    }
    ```
*   **Error Response (404 Not Found)**: If node doesn't exist.

---

### 8. Set Node Status (Single)
*   **PUT** `/api/mindmap/nodes/{nodeId}/status`
*   **Description**: Sets the status of a single node. Triggers status recalculation for children (downwards) and parents (upwards).
*   **Path Parameter**: `nodeId` (Long)
*   **Request Body**: `NodeStatus` enum string (e.g., "TESTED", "CANCELLED", "PENDING_TEST")
*   **cURL Example**:
    ```bash
    curl -X PUT -H "Content-Type: application/json" -d '"TESTED"' http://localhost:8080/api/mindmap/nodes/201/status
    ```
    (Node 201 is "Login with valid credentials")
*   **Success Response (200 OK)**:
    ```json
    {
        "id": 201,
        "status": "TESTED",
        // ... other fields ...
        // Note: The response is the node whose status was directly set.
        // Children and parent statuses are updated as a side effect.
    }
    ```
*   **Error Response (404 Not Found)**: If node doesn't exist.
*   **Error Response (400 Bad Request)**: If status string is invalid.

---

### 9. Batch Set Node Status
*   **PUT** `/api/mindmap/nodes/status/batch`
*   **Description**: Sets the status for multiple nodes. Triggers status recalculations for children and parents of affected nodes.
*   **Request Body**: `BatchStatusUpdateRequest` object (JSON)
    ```json
    {
        "nodeIds": [201, 202], // List of node IDs
        "status": "CANCELLED"  // New status for these nodes
    }
    ```
*   **cURL Example**:
    ```bash
    curl -X PUT -H "Content-Type: application/json" -d '{
        "nodeIds": [201, 202],
        "status": "CANCELLED"
    }' http://localhost:8080/api/mindmap/nodes/status/batch
    ```
*   **Success Response (200 OK)**: List of nodes from the batch that were processed.
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
*   **Error Response (400 Bad Request)**: If request is malformed (e.g., missing fields, invalid status).
