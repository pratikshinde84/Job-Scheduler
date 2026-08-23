package com.jobscheduler.controller;

import com.jobscheduler.dto.JobRequest;
import com.jobscheduler.entity.Job;
import com.jobscheduler.entity.Queue;
import com.jobscheduler.repository.QueueRepository;
import com.jobscheduler.service.JobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobControllerTest {

    @Mock
    private JobService jobService;

    @Mock
    private QueueRepository queueRepository;

    private JobController jobController;

    @BeforeEach
    void setUp() {
        jobController = new JobController(jobService, queueRepository);
    }

    @Test
    void testEnqueueUpdatesQueueConcurrencyLimit() {
        UUID queueId = UUID.randomUUID();
        Queue queue = Queue.builder().id(queueId).concurrencyLimit(2).build();
        Job job = Job.builder().id(UUID.randomUUID()).queue(queue).build();

        when(queueRepository.findById(queueId)).thenReturn(Optional.of(queue));
        when(jobService.enqueue(any(), any(), anyInt(), any(), any(), anyInt(), any())).thenReturn(job);

        JobRequest req = new JobRequest(Map.of("key", "val"), 1, null, null, 3, null, 10);
        var response = jobController.enqueue(queueId, req);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(10, queue.getConcurrencyLimit());
        verify(queueRepository).save(queue);
    }

    @Test
    void testEnqueueDefaultsConcurrencyToFive() {
        UUID queueId = UUID.randomUUID();
        Queue queue = Queue.builder().id(queueId).concurrencyLimit(2).build();
        Job job = Job.builder().id(UUID.randomUUID()).queue(queue).build();

        when(queueRepository.findById(queueId)).thenReturn(Optional.of(queue));
        when(jobService.enqueue(any(), any(), anyInt(), any(), any(), anyInt(), any())).thenReturn(job);

        JobRequest req = new JobRequest(Map.of("key", "val"), 0, null, null, 3, null, null);
        var response = jobController.enqueue(queueId, req);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(5, queue.getConcurrencyLimit());
        verify(queueRepository).save(queue);
    }
}
