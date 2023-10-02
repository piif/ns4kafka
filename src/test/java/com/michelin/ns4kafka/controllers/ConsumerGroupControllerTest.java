package com.michelin.ns4kafka.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.michelin.ns4kafka.models.AuditLog;
import com.michelin.ns4kafka.models.Namespace;
import com.michelin.ns4kafka.models.ObjectMeta;
import com.michelin.ns4kafka.models.consumer.group.ConsumerGroupResetOffsets;
import com.michelin.ns4kafka.models.consumer.group.ConsumerGroupResetOffsets.ConsumerGroupResetOffsetsSpec;
import com.michelin.ns4kafka.models.consumer.group.ConsumerGroupResetOffsets.ResetOffsetsMethod;
import com.michelin.ns4kafka.models.consumer.group.ConsumerGroupResetOffsetsResponse;
import com.michelin.ns4kafka.security.ResourceBasedSecurityRule;
import com.michelin.ns4kafka.services.ConsumerGroupService;
import com.michelin.ns4kafka.services.NamespaceService;
import com.michelin.ns4kafka.utils.exceptions.ResourceValidationException;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.security.utils.SecurityService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsumerGroupControllerTest {
    @Mock
    NamespaceService namespaceService;

    @Mock
    ConsumerGroupService consumerGroupService;

    @Mock
    ApplicationEventPublisher<AuditLog> applicationEventPublisher;

    @Mock
    SecurityService securityService;

    @InjectMocks
    ConsumerGroupController consumerGroupController;

    @Test
    void resetSuccess() throws InterruptedException, ExecutionException {
        Namespace ns = Namespace.builder()
            .metadata(ObjectMeta.builder()
                .name("test")
                .cluster("local")
                .build())
            .build();

        ConsumerGroupResetOffsets resetOffset = ConsumerGroupResetOffsets.builder()
            .metadata(ObjectMeta.builder()
                .name("groupID")
                .cluster("local")
                .build())
            .spec(ConsumerGroupResetOffsetsSpec.builder()
                .topic("topic1")
                .method(ResetOffsetsMethod.TO_EARLIEST)
                .options(null)
                .build())
            .build();

        TopicPartition topicPartition1 = new TopicPartition("topic1", 0);
        TopicPartition topicPartition2 = new TopicPartition("topic1", 1);
        List<TopicPartition> topicPartitions = List.of(topicPartition1, topicPartition2);

        when(namespaceService.findByName("test"))
            .thenReturn(Optional.of(ns));
        when(consumerGroupService.validateResetOffsets(resetOffset))
            .thenReturn(List.of());
        when(consumerGroupService.isNamespaceOwnerOfConsumerGroup("test", "groupID"))
            .thenReturn(true);
        when(consumerGroupService.getConsumerGroupStatus(ns, "groupID"))
            .thenReturn("Empty");
        when(consumerGroupService.getPartitionsToReset(ns, "groupID", "topic1"))
            .thenReturn(topicPartitions);
        when(consumerGroupService.prepareOffsetsToReset(ns, "groupID", null, topicPartitions,
            ResetOffsetsMethod.TO_EARLIEST))
            .thenReturn(Map.of(topicPartition1, 5L, topicPartition2, 10L));
        when(securityService.username()).thenReturn(Optional.of("test-user"));
        when(securityService.hasRole(ResourceBasedSecurityRule.IS_ADMIN)).thenReturn(false);
        doNothing().when(applicationEventPublisher).publishEvent(any());

        List<ConsumerGroupResetOffsetsResponse> result =
            consumerGroupController.resetOffsets("test", "groupID", resetOffset, false);

        ConsumerGroupResetOffsetsResponse resultTopicPartition1 = result
            .stream()
            .filter(topicPartitionOffset -> topicPartitionOffset.getSpec().getPartition() == 0)
            .findFirst()
            .orElse(null);

        assertNotNull(resultTopicPartition1);
        assertEquals(5L, resultTopicPartition1.getSpec().getOffset());

        ConsumerGroupResetOffsetsResponse resultTopicPartition2 = result
            .stream()
            .filter(topicPartitionOffset -> topicPartitionOffset.getSpec().getPartition() == 1)
            .findFirst()
            .orElse(null);

        assertNotNull(resultTopicPartition2);
        assertEquals(10L, resultTopicPartition2.getSpec().getOffset());

        verify(consumerGroupService, times(1)).alterConsumerGroupOffsets(ArgumentMatchers.eq(ns),
            ArgumentMatchers.eq("groupID"), anyMap());
    }

    @Test
    void resetDryRunSuccess() throws InterruptedException, ExecutionException {
        Namespace ns = Namespace.builder()
            .metadata(ObjectMeta.builder()
                .name("test")
                .cluster("local")
                .build())
            .build();

        ConsumerGroupResetOffsets resetOffset = ConsumerGroupResetOffsets.builder()
            .metadata(ObjectMeta.builder()
                .name("groupID")
                .cluster("local")
                .build())
            .spec(ConsumerGroupResetOffsetsSpec.builder()
                .topic("topic1")
                .method(ResetOffsetsMethod.TO_EARLIEST)
                .build())
            .build();

        TopicPartition topicPartition1 = new TopicPartition("topic1", 0);
        TopicPartition topicPartition2 = new TopicPartition("topic1", 1);
        List<TopicPartition> topicPartitions = List.of(topicPartition1, topicPartition2);

        when(namespaceService.findByName("test"))
            .thenReturn(Optional.of(ns));
        when(consumerGroupService.validateResetOffsets(resetOffset))
            .thenReturn(List.of());
        when(consumerGroupService.isNamespaceOwnerOfConsumerGroup("test", "groupID"))
            .thenReturn(true);
        when(consumerGroupService.getConsumerGroupStatus(ns, "groupID"))
            .thenReturn("Empty");
        when(consumerGroupService.getPartitionsToReset(ns, "groupID", "topic1"))
            .thenReturn(topicPartitions);
        when(consumerGroupService.prepareOffsetsToReset(ns, "groupID", null, topicPartitions,
            ResetOffsetsMethod.TO_EARLIEST))
            .thenReturn(Map.of(topicPartition1, 5L, topicPartition2, 10L));

        List<ConsumerGroupResetOffsetsResponse> result =
            consumerGroupController.resetOffsets("test", "groupID", resetOffset, true);

        ConsumerGroupResetOffsetsResponse resultTopicPartition1 = result
            .stream()
            .filter(topicPartitionOffset -> topicPartitionOffset.getSpec().getPartition() == 0)
            .findFirst()
            .orElse(null);

        assertNotNull(resultTopicPartition1);
        assertEquals(5L, resultTopicPartition1.getSpec().getOffset());

        ConsumerGroupResetOffsetsResponse resultTopicPartition2 = result
            .stream()
            .filter(topicPartitionOffset -> topicPartitionOffset.getSpec().getPartition() == 1)
            .findFirst()
            .orElse(null);

        assertNotNull(resultTopicPartition2);
        assertEquals(10L, resultTopicPartition2.getSpec().getOffset());

        verify(consumerGroupService, never()).alterConsumerGroupOffsets(notNull(), anyString(), anyMap());
    }

    @Test
    void resetExecutionError() throws InterruptedException, ExecutionException {
        Namespace ns = Namespace.builder()
            .metadata(ObjectMeta.builder()
                .name("test")
                .cluster("local")
                .build())
            .build();

        ConsumerGroupResetOffsets resetOffset = ConsumerGroupResetOffsets.builder()
            .metadata(ObjectMeta.builder()
                .name("groupID")
                .cluster("local")
                .build())
            .spec(ConsumerGroupResetOffsetsSpec.builder()
                .topic("topic1")
                .method(ResetOffsetsMethod.TO_EARLIEST)
                .build())
            .build();

        when(namespaceService.findByName("test"))
            .thenReturn(Optional.of(ns));
        when(consumerGroupService.validateResetOffsets(resetOffset))
            .thenReturn(List.of());
        when(consumerGroupService.isNamespaceOwnerOfConsumerGroup("test", "groupID"))
            .thenReturn(true);
        when(consumerGroupService.getConsumerGroupStatus(ns, "groupID"))
            .thenReturn("Empty");
        when(consumerGroupService.getPartitionsToReset(ns, "groupID", "topic1"))
            .thenThrow(new ExecutionException("Error during getPartitionsToReset", new Throwable()));

        ExecutionException result = assertThrows(ExecutionException.class,
            () -> consumerGroupController.resetOffsets("test", "groupID", resetOffset, false));

        assertEquals("Error during getPartitionsToReset", result.getMessage());
    }

    @Test
    void resetValidationErrorNotOwnerOfConsumerGroup() {
        ConsumerGroupResetOffsets resetOffset = ConsumerGroupResetOffsets.builder()
            .metadata(ObjectMeta.builder()
                .name("groupID")
                .cluster("local")
                .build())
            .spec(ConsumerGroupResetOffsetsSpec.builder()
                .topic("topic1")
                .method(ResetOffsetsMethod.TO_EARLIEST)
                .build())
            .build();

        when(consumerGroupService.validateResetOffsets(resetOffset))
            .thenReturn(new ArrayList<>());
        when(consumerGroupService.isNamespaceOwnerOfConsumerGroup("test", "groupID"))
            .thenReturn(false);

        ResourceValidationException result = assertThrows(ResourceValidationException.class,
            () -> consumerGroupController.resetOffsets("test", "groupID", resetOffset, false));

        assertLinesMatch(List.of("Namespace not owner of this consumer group \"groupID\"."),
            result.getValidationErrors());
    }

    @Test
    void resetValidationErrorInvalidResource() {
        ConsumerGroupResetOffsets resetOffset = ConsumerGroupResetOffsets.builder()
            .metadata(ObjectMeta.builder()
                .name("groupID")
                .cluster("local")
                .build())
            .spec(ConsumerGroupResetOffsetsSpec.builder()
                .topic("topic1")
                .method(ResetOffsetsMethod.TO_EARLIEST)
                .build())
            .build();

        when(consumerGroupService.validateResetOffsets(resetOffset))
            .thenReturn(List.of("Validation Error"));
        when(consumerGroupService.isNamespaceOwnerOfConsumerGroup("test", "groupID"))
            .thenReturn(true);

        ResourceValidationException result = assertThrows(ResourceValidationException.class,
            () -> consumerGroupController.resetOffsets("test", "groupID", resetOffset, false));

        assertLinesMatch(List.of("Validation Error"), result.getValidationErrors());
    }

    @Test
    void resetValidationErrorConsumerGroupActive() throws ExecutionException, InterruptedException {
        Namespace ns = Namespace.builder()
            .metadata(ObjectMeta.builder()
                .name("test")
                .cluster("local")
                .build())
            .build();

        ConsumerGroupResetOffsets resetOffset = ConsumerGroupResetOffsets.builder()
            .metadata(ObjectMeta.builder()
                .name("groupID")
                .cluster("local")
                .build())
            .spec(ConsumerGroupResetOffsetsSpec.builder()
                .topic("topic1")
                .method(ResetOffsetsMethod.TO_EARLIEST)
                .build())
            .build();

        when(namespaceService.findByName("test"))
            .thenReturn(Optional.of(ns));
        when(consumerGroupService.validateResetOffsets(resetOffset))
            .thenReturn(new ArrayList<>());
        when(consumerGroupService.isNamespaceOwnerOfConsumerGroup("test", "groupID"))
            .thenReturn(true);
        when(consumerGroupService.getConsumerGroupStatus(ns, "groupID"))
            .thenReturn("Active");

        IllegalStateException result = assertThrows(IllegalStateException.class,
            () -> consumerGroupController.resetOffsets("test", "groupID", resetOffset, false));

        assertEquals(
            "Assignments can only be reset if the consumer group \"groupID\" is inactive, "
                + "but the current state is active.",
            result.getMessage());
    }
}
