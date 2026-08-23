package com.jobscheduler.worker;

import com.jobscheduler.entity.Job;
import com.jobscheduler.entity.Queue;
import com.jobscheduler.executor.JobExecutor;
import com.jobscheduler.repository.QueueRepository;
import com.jobscheduler.service.JobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobPollerTest {

    @Mock
    private QueueRepository queueRepository;

    @Mock
    private JobService jobService;

    @Mock
    private JobRunner jobRunner;

    @Mock
    private JobExecutor jobExecutor;

    private JobPoller jobPoller;

    @BeforeEach
    void setUp() {
        lenient().when(jobExecutor.queueName()).thenReturn("test-queue");
        jobPoller = new JobPoller(queueRepository, jobService, jobRunner, "worker", 4, List.of(jobExecutor));
    }

    @Test
    void testWorkerInitialization() {
        List<String> workers = jobPoller.getWorkerNames();
        assertEquals(4, workers.size());
        assertTrue(workers.contains("worker-1"));
        assertTrue(workers.contains("worker-4"));
    }

    @Test
    void testAddWorker() {
        jobPoller.addWorker("worker-5");
        List<String> workers = jobPoller.getWorkerNames();
        assertEquals(5, workers.size());
        assertTrue(workers.contains("worker-5"));
    }

    @Test
    void testPollNoActiveQueues() {
        when(queueRepository.findAllActive()).thenReturn(Collections.emptyList());
        assertDoesNotThrow(() -> jobPoller.poll());
    }

    @Test
    void testPollClaimAndExecuteJob() {
        UUID queueId = UUID.randomUUID();
        Queue queue = Queue.builder()
                .id(queueId)
                .name("test-queue")
                .concurrencyLimit(5)
                .build();

        Job job = Job.builder()
                .id(UUID.randomUUID())
                .status(Job.JobStatus.pending)
                .build();

        when(queueRepository.findAllActive()).thenReturn(List.of(queue));
        when(jobService.claimJobs(eq(queueId), anyString(), eq(1)))
                .thenReturn(List.of(job)).thenReturn(List.of());

        jobPoller.poll();

        verify(jobService, atLeastOnce()).claimJobs(eq(queueId), anyString(), eq(1));
        verify(jobRunner, atLeastOnce()).executeAsync(eq(job), eq("test-queue"), anyString(), any(), eq(jobExecutor));
    }
}
