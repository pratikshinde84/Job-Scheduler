package com.jobscheduler.controller;

import com.jobscheduler.dto.DashboardResponse;
import com.jobscheduler.entity.User;
import com.jobscheduler.entity.Worker;
import com.jobscheduler.repository.JobRepository;
import com.jobscheduler.repository.ProjectRepository;
import com.jobscheduler.repository.QueueRepository;
import com.jobscheduler.repository.WorkerRepository;
import com.jobscheduler.security.UserContext;
import com.jobscheduler.worker.CronScheduler;
import com.jobscheduler.worker.JobPoller;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private QueueRepository queueRepository;

    @Mock
    private JobRepository jobRepository;

    @Mock
    private WorkerRepository workerRepository;

    @Mock
    private CronScheduler cronScheduler;

    @Mock
    private JobPoller jobPoller;

    private DashboardController dashboardController;
    private MockedStatic<UserContext> userContextMock;

    @BeforeEach
    void setUp() {
        dashboardController = new DashboardController(
                projectRepository, queueRepository, jobRepository, workerRepository, cronScheduler, jobPoller);

        User mockUser = new User();
        mockUser.setId(UUID.randomUUID());
        userContextMock = Mockito.mockStatic(UserContext.class);
        userContextMock.when(UserContext::get).thenReturn(mockUser);
    }

    @AfterEach
    void tearDown() {
        userContextMock.close();
    }

    @Test
    void testAddWorker() {
        when(workerRepository.findAll()).thenReturn(List.of(
                Worker.builder().name("worker-1").build(),
                Worker.builder().name("worker-2").build()));
        when(workerRepository.save(any(Worker.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<DashboardResponse.WorkerSummary> response = dashboardController.addWorker();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("worker-3", response.getBody().name());
        verify(cronScheduler).addWorker("worker-3");
        verify(jobPoller).addWorker("worker-3");
    }

    @Test
    void testGetDashboardFiltersOfflineWorkers() {
        Worker activeWorker = Worker.builder()
                .name("worker-1")
                .status(Worker.WorkerStatus.active)
                .lastHeartbeatAt(OffsetDateTime.now())
                .build();

        Worker offlineWorker = Worker.builder()
                .name("stale-worker")
                .status(Worker.WorkerStatus.offline)
                .lastHeartbeatAt(OffsetDateTime.now().minusMinutes(10))
                .build();

        when(workerRepository.findAll()).thenReturn(List.of(activeWorker, offlineWorker));
        when(queueRepository.findByUserId(any())).thenReturn(List.of());
        when(projectRepository.findByUserIdOrderByCreatedAtDesc(any())).thenReturn(List.of());

        ResponseEntity<DashboardResponse> response = dashboardController.dashboard();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        verify(workerRepository).deleteAll(argThat((List<Worker> list) -> list.contains(offlineWorker)));
    }
}
