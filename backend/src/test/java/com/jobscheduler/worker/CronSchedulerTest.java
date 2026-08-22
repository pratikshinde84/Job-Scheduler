package com.jobscheduler.worker;

import com.jobscheduler.entity.Worker;
import com.jobscheduler.repository.JobRepository;
import com.jobscheduler.repository.WorkerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CronSchedulerTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private WorkerRepository workerRepository;

    private CronScheduler cronScheduler;

    @BeforeEach
    void setUp() {
        cronScheduler = new CronScheduler(jobRepository, workerRepository, "worker", 4);
    }

    @Test
    void testHeartbeatUpdatesWorker() {
        when(workerRepository.upsertHeartbeat(anyString(), any(), anyString()))
                .thenReturn(1);

        assertDoesNotThrow(() -> cronScheduler.heartbeat());

        verify(workerRepository, times(4)).upsertHeartbeat(anyString(), any(), anyString());
    }

    @Test
    void testAddWorkerDynamically() {
        cronScheduler.addWorker("worker-5");
        when(workerRepository.upsertHeartbeat(anyString(), any(), anyString()))
                .thenReturn(1);

        cronScheduler.heartbeat();

        verify(workerRepository, times(5)).upsertHeartbeat(anyString(), any(), anyString());
    }
}
