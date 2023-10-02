/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.michelin.ns4kafka.services.clients.connect.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;

/**
 * Unique ID for a single task. It includes a unique connector ID and a task ID that is unique within
 * the connector.
 */
public record ConnectorTaskId(@JsonProperty("connector") String connector, @JsonProperty("task") int task)
    implements Serializable, Comparable<ConnectorTaskId> {
    @Override
    public int compareTo(ConnectorTaskId o) {
        int connectorCmp = connector.compareTo(o.connector);
        if (connectorCmp != 0) {
            return connectorCmp;
        }
        return Integer.compare(task, o.task);
    }
}
