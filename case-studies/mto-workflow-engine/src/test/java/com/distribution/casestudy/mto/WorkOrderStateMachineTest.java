package com.distribution.casestudy.mto;

import com.distribution.casestudy.mto.config.ProcessRouteConfig;
import com.distribution.casestudy.mto.model.*;
import com.distribution.casestudy.mto.service.WorkOrderStateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link WorkOrderStateMachine}.
 *
 * <p>Covers all state transitions, guard checks, and the process completion
 * auto-detection pattern extracted from the jewelry ERP.</p>
 */
class WorkOrderStateMachineTest {

    private ProcessRouteConfig routeConfig;
    private WorkOrderStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        routeConfig = new ProcessRouteConfig();
        // Set up a test route
        ProcessRouteConfig.RouteDefinition route = new ProcessRouteConfig.RouteDefinition();
        route.setName("test-route");
        route.setDescription("Test route with 3 processes");

        ProcessRouteConfig.ProcessDefinition p1 = new ProcessRouteConfig.ProcessDefinition();
        p1.setCode("P1"); p1.setName("Process 1"); p1.setSortOrder(1); p1.setPlannedHours(BigDecimal.valueOf(2));
        ProcessRouteConfig.ProcessDefinition p2 = new ProcessRouteConfig.ProcessDefinition();
        p2.setCode("P2"); p2.setName("Process 2"); p2.setSortOrder(2); p2.setPlannedHours(BigDecimal.valueOf(3));
        p2.setNeedInspection(true);
        ProcessRouteConfig.ProcessDefinition p3 = new ProcessRouteConfig.ProcessDefinition();
        p3.setCode("P3"); p3.setName("Process 3"); p3.setSortOrder(3); p3.setPlannedHours(BigDecimal.valueOf(1));

        route.setProcesses(List.of(p1, p2, p3));
        routeConfig.setRoutes(List.of(route));

        stateMachine = new WorkOrderStateMachine(routeConfig);
    }

    // ========== Create ==========

    @Nested
    @DisplayName("create()")
    class CreateTests {

        @Test
        @DisplayName("Should create work order in PENDING status with processes from route")
        void shouldCreateWithRoute() {
            WorkOrder wo = stateMachine.create("WO-001", "test-route");

            assertEquals(WorkOrderStatus.PENDING, wo.getStatus());
            assertEquals("WO-001", wo.getWorkOrderNo());
            assertEquals(3, wo.getProcesses().size());
            assertEquals("Process 1", wo.getProcesses().get(0).getProcessName());
            assertEquals(1, wo.getProcesses().get(0).getSortOrder());
            assertEquals("Process 3", wo.getProcesses().get(2).getProcessName());
            // All processes should start as PENDING
            wo.getProcesses().forEach(p -> assertEquals(ProcessStatus.PENDING, p.getStatus()));
        }

        @Test
        @DisplayName("Should throw when route not found")
        void shouldThrowOnUnknownRoute() {
            assertThrows(IllegalArgumentException.class,
                    () -> stateMachine.create("WO-001", "nonexistent-route"));
        }
    }

    // ========== Schedule ==========

    @Nested
    @DisplayName("schedule()")
    class ScheduleTests {

        @Test
        @DisplayName("Should transition from PENDING to SCHEDULED")
        void shouldScheduleFromPending() {
            WorkOrder wo = stateMachine.create("WO-001", "test-route");
            WorkCenter wc = new WorkCenter(1L, "Workshop A");
            Worker worker = new Worker(1L, "John");

            stateMachine.schedule(wo, wc, worker);

            assertEquals(WorkOrderStatus.SCHEDULED, wo.getStatus());
            assertEquals(wc, wo.getWorkCenter());
            assertEquals(worker, wo.getAssignedWorker());
        }

        @Test
        @DisplayName("Should throw when not in PENDING or MATERIAL_READY")
        void shouldThrowWhenNotPending() {
            WorkOrder wo = stateMachine.create("WO-001", "test-route");
            WorkCenter wc = new WorkCenter(1L, "Workshop A");
            Worker worker = new Worker(1L, "John");

            // Start work to move to PRODUCING
            stateMachine.schedule(wo, wc, worker);
            stateMachine.startWork(wo);

            assertThrows(IllegalStateException.class,
                    () -> stateMachine.schedule(wo, wc, worker));
        }
    }

    // ========== Start Work ==========

    @Nested
    @DisplayName("startWork()")
    class StartWorkTests {

        @Test
        @DisplayName("Should transition from SCHEDULED to PRODUCING")
        void shouldStartWork() {
            WorkOrder wo = stateMachine.create("WO-001", "test-route");
            stateMachine.schedule(wo, new WorkCenter(1L, "W1"), new Worker(1L, "John"));
            stateMachine.startWork(wo);

            assertEquals(WorkOrderStatus.PRODUCING, wo.getStatus());
            assertNotNull(wo.getActualStartDate());
        }

        @Test
        @DisplayName("Should throw when not in SCHEDULED")
        void shouldThrowWhenNotScheduled() {
            WorkOrder wo = stateMachine.create("WO-001", "test-route");
            assertThrows(IllegalStateException.class, () -> stateMachine.startWork(wo));
        }
    }

    // ========== Finish Process ==========

    @Nested
    @DisplayName("finishProcess()")
    class FinishProcessTests {

        @Test
        @DisplayName("Should transition to PARTIAL_FINISHED when some processes remain")
        void shouldTransitionToPartialFinished() {
            WorkOrder wo = createAndStartWorkOrder();
            WorkOrderProcess p1 = wo.getProcesses().get(0);
            p1.setId(1L);

            stateMachine.finishProcess(wo, 1L);

            assertEquals(WorkOrderStatus.PARTIAL_FINISHED, wo.getStatus());
            assertEquals(ProcessStatus.COMPLETED, p1.getStatus());
            assertNotNull(p1.getFinishedAt());
        }

        @Test
        @DisplayName("Should auto-detect ALL_FINISHED when last process completes")
        void shouldAutoDetectAllFinished() {
            WorkOrder wo = createAndStartWorkOrder();
            // Assign IDs
            for (int i = 0; i < wo.getProcesses().size(); i++) {
                wo.getProcesses().get(i).setId((long) (i + 1));
            }

            // Complete all 3 processes
            stateMachine.finishProcess(wo, 1L);
            assertEquals(WorkOrderStatus.PARTIAL_FINISHED, wo.getStatus());

            stateMachine.finishProcess(wo, 2L);
            assertEquals(WorkOrderStatus.PARTIAL_FINISHED, wo.getStatus());

            stateMachine.finishProcess(wo, 3L);
            // All done → auto-finish
            assertEquals(WorkOrderStatus.ALL_FINISHED, wo.getStatus());
            assertNotNull(wo.getActualEndDate());
        }

        @Test
        @DisplayName("Should throw when process not found")
        void shouldThrowOnUnknownProcess() {
            WorkOrder wo = createAndStartWorkOrder();
            assertThrows(IllegalArgumentException.class,
                    () -> stateMachine.finishProcess(wo, 999L));
        }

        private WorkOrder createAndStartWorkOrder() {
            WorkOrder wo = stateMachine.create("WO-001", "test-route");
            stateMachine.schedule(wo, new WorkCenter(1L, "W1"), new Worker(1L, "John"));
            stateMachine.startWork(wo);
            return wo;
        }
    }

    // ========== Finish Work ==========

    @Nested
    @DisplayName("finishWork()")
    class FinishWorkTests {

        @Test
        @DisplayName("Should manually finish all processes and transition to ALL_FINISHED")
        void shouldManuallyFinishAll() {
            WorkOrder wo = stateMachine.create("WO-001", "test-route");
            stateMachine.schedule(wo, new WorkCenter(1L, "W1"), new Worker(1L, "John"));
            stateMachine.startWork(wo);

            stateMachine.finishWork(wo);

            assertEquals(WorkOrderStatus.ALL_FINISHED, wo.getStatus());
            assertNotNull(wo.getActualEndDate());
            // All processes should be marked completed
            wo.getProcesses().forEach(p -> assertEquals(ProcessStatus.COMPLETED, p.getStatus()));
        }
    }

    // ========== Close Work ==========

    @Nested
    @DisplayName("closeWork()")
    class CloseWorkTests {

        @Test
        @DisplayName("Should transition from ALL_FINISHED to CLOSED")
        void shouldClose() {
            WorkOrder wo = stateMachine.create("WO-001", "test-route");
            stateMachine.schedule(wo, new WorkCenter(1L, "W1"), new Worker(1L, "John"));
            stateMachine.startWork(wo);
            stateMachine.finishWork(wo);
            stateMachine.closeWork(wo);

            assertEquals(WorkOrderStatus.CLOSED, wo.getStatus());
        }

        @Test
        @DisplayName("Should throw when not ALL_FINISHED or WAREHOUSED")
        void shouldThrowWhenNotFinished() {
            WorkOrder wo = stateMachine.create("WO-001", "test-route");
            assertThrows(IllegalStateException.class, () -> stateMachine.closeWork(wo));
        }
    }

    // ========== Void Work ==========

    @Nested
    @DisplayName("voidWork()")
    class VoidWorkTests {

        @Test
        @DisplayName("Should void from any non-terminal state")
        void shouldVoidFromProducing() {
            WorkOrder wo = stateMachine.create("WO-001", "test-route");
            stateMachine.schedule(wo, new WorkCenter(1L, "W1"), new Worker(1L, "John"));
            stateMachine.startWork(wo);

            stateMachine.voidWork(wo, "Customer cancelled");

            assertEquals(WorkOrderStatus.VOID, wo.getStatus());
            assertEquals("Customer cancelled", wo.getRemark());
        }

        @Test
        @DisplayName("Should void from PENDING")
        void shouldVoidFromPending() {
            WorkOrder wo = stateMachine.create("WO-001", "test-route");
            stateMachine.voidWork(wo, "Duplicate order");
            assertEquals(WorkOrderStatus.VOID, wo.getStatus());
        }

        @Test
        @DisplayName("Should throw when already CLOSED")
        void shouldThrowWhenClosed() {
            WorkOrder wo = stateMachine.create("WO-001", "test-route");
            stateMachine.schedule(wo, new WorkCenter(1L, "W1"), new Worker(1L, "John"));
            stateMachine.startWork(wo);
            stateMachine.finishWork(wo);
            stateMachine.closeWork(wo);

            assertThrows(IllegalStateException.class,
                    () -> stateMachine.voidWork(wo, "too late"));
        }

        @Test
        @DisplayName("Should throw when already VOID")
        void shouldThrowWhenAlreadyVoid() {
            WorkOrder wo = stateMachine.create("WO-001", "test-route");
            stateMachine.voidWork(wo, "once");

            assertThrows(IllegalStateException.class,
                    () -> stateMachine.voidWork(wo, "twice"));
        }
    }

    // ========== Full Lifecycle ==========

    @Nested
    @DisplayName("Full lifecycle")
    class FullLifecycleTests {

        @Test
        @DisplayName("Happy path: create → schedule → start → finish processes → close")
        void happyPath() {
            WorkOrder wo = stateMachine.create("WO-001", "test-route");

            // Assign IDs to processes
            for (int i = 0; i < wo.getProcesses().size(); i++) {
                wo.getProcesses().get(i).setId((long) (i + 1));
            }

            // Schedule
            stateMachine.schedule(wo, new WorkCenter(1L, "Workshop A"), new Worker(1L, "Alice"));
            assertEquals(WorkOrderStatus.SCHEDULED, wo.getStatus());

            // Start
            stateMachine.startWork(wo);
            assertEquals(WorkOrderStatus.PRODUCING, wo.getStatus());

            // Process 1 done
            stateMachine.finishProcess(wo, 1L);
            assertEquals(WorkOrderStatus.PARTIAL_FINISHED, wo.getStatus());

            // Process 2 done
            stateMachine.finishProcess(wo, 2L);
            assertEquals(WorkOrderStatus.PARTIAL_FINISHED, wo.getStatus());

            // Process 3 done → auto all finished
            stateMachine.finishProcess(wo, 3L);
            assertEquals(WorkOrderStatus.ALL_FINISHED, wo.getStatus());

            // Close
            stateMachine.closeWork(wo);
            assertEquals(WorkOrderStatus.CLOSED, wo.getStatus());
        }

        @Test
        @DisplayName("Cancel path: create → schedule → void")
        void cancelPath() {
            WorkOrder wo = stateMachine.create("WO-002", "test-route");
            stateMachine.schedule(wo, new WorkCenter(1L, "W1"), new Worker(1L, "Bob"));
            stateMachine.voidWork(wo, "Design changed");

            assertEquals(WorkOrderStatus.VOID, wo.getStatus());
        }
    }
}
