/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.operations;

import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.concurrent.ParallelismConfiguration;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.GradleThread;
import org.gradle.internal.concurrent.ManagedExecutor;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.exceptions.DefaultMultiCauseException;
import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@ServiceScope(Scopes.BuildSession.class)
public class DefaultBuildOperationExecutor extends DefaultBuildOperationRunner implements BuildOperationExecutor, Stoppable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultBuildOperationExecutor.class);
    private static final String LINE_SEPARATOR = SystemProperties.getInstance().getLineSeparator();

    private final Clock clock;
    private final ProgressLoggerFactory progressLoggerFactory;
    private final BuildOperationQueueFactory buildOperationQueueFactory;
    private final ManagedExecutor fixedSizePool;

    public DefaultBuildOperationExecutor(
        BuildOperationListener listener,
        Clock clock,
        ProgressLoggerFactory progressLoggerFactory,
        BuildOperationQueueFactory buildOperationQueueFactory,
        ExecutorFactory executorFactory,
        ParallelismConfiguration parallelismConfiguration,
        BuildOperationIdFactory buildOperationIdFactory
    ) {
        super(listener, clock::getCurrentTime, buildOperationIdFactory);
        this.clock = clock;
        this.progressLoggerFactory = progressLoggerFactory;
        this.buildOperationQueueFactory = buildOperationQueueFactory;
        this.fixedSizePool = executorFactory.create("Build operations", parallelismConfiguration.getMaxWorkerCount());
    }

    @Override
    public void run(RunnableBuildOperation buildOperation) {
        try {
            super.run(buildOperation);
        } finally {
            maybeStopUnmanagedThreadOperation();
        }
    }

    @Override
    public <T> T call(CallableBuildOperation<T> buildOperation) {
        try {
            return super.call(buildOperation);
        } finally {
            maybeStopUnmanagedThreadOperation();
        }
    }

    @Override
    public <O extends RunnableBuildOperation> void runAll(Action<BuildOperationQueue<O>> schedulingAction) {
        try {
            executeInParallel(new ParentPreservingQueueWorker<>(RUNNABLE_BUILD_OPERATION_WORKER), schedulingAction);
        } finally {
            maybeStopUnmanagedThreadOperation();
        }
    }

    @Override
    public <O extends BuildOperation> void runAll(BuildOperationWorker<O> worker, Action<BuildOperationQueue<O>> schedulingAction) {
        try {
            executeInParallel(new ParentPreservingQueueWorker<>(worker), schedulingAction);
        } finally {
            maybeStopUnmanagedThreadOperation();
        }
    }

    private <O extends BuildOperation> void executeInParallel(BuildOperationQueue.QueueWorker<O> worker, Action<BuildOperationQueue<O>> queueAction) {
        BuildOperationQueue<O> queue = buildOperationQueueFactory.create(fixedSizePool, worker);

        List<GradleException> failures = Lists.newArrayList();
        try {
            queueAction.execute(queue);
        } catch (Exception e) {
            failures.add(new BuildOperationQueueFailure("There was a failure while populating the build operation queue: " + e.getMessage(), e));
            queue.cancel();
        }

        try {
            queue.waitForCompletion();
        } catch (MultipleBuildOperationFailures e) {
            failures.add(e);
        }

        if (failures.size() == 1) {
            throw failures.get(0);
        } else if (failures.size() > 1) {
            throw new DefaultMultiCauseException(formatMultipleFailureMessage(failures), failures);
        }
    }

    @Override
    protected BuildOperationExecutionListener createListener(@Nullable BuildOperationState parent, BuildOperationDescriptor descriptor) {
        final BuildOperationExecutionListener delegate = super.createListener(parent, descriptor);
        return new BuildOperationExecutionListener() {

            private ProgressLogger progressLogger;

            @Override
            public void start(BuildOperationState operationState) {
                delegate.start(operationState);
                progressLogger = createProgressLogger(operationState);
            }

            @Override
            public void stop(BuildOperationState operationState, DefaultBuildOperationContext context) {
                progressLogger.completed(context.status, context.failure != null);
                delegate.stop(operationState, context);
            }

            @Override
            public void close(BuildOperationState operationState) {
                delegate.close(operationState);
            }
        };
    }

    @Nullable
    @Override
    protected BuildOperationState determineParent(BuildOperationDescriptor.Builder descriptorBuilder, @Nullable DefaultBuildOperationRunner.BuildOperationState defaultParent) {
        return maybeStartUnmanagedThreadOperation(super.determineParent(descriptorBuilder, defaultParent));
    }

    private ProgressLogger createProgressLogger(BuildOperationState currentOperation) {
        BuildOperationDescriptor descriptor = currentOperation.getDescription();
        ProgressLogger progressLogger = progressLoggerFactory.newOperation(DefaultBuildOperationExecutor.class, descriptor);
        return progressLogger.start(descriptor.getDisplayName(), descriptor.getProgressDisplayName());
    }

    @Nullable
    private BuildOperationState maybeStartUnmanagedThreadOperation(@Nullable BuildOperationState parentState) {
        if (parentState == null && !GradleThread.isManaged()) {
            parentState = UnmanagedThreadOperation.create(clock.getCurrentTime());
            parentState.setRunning(true);
            setCurrentBuildOperation(parentState);
            listener.started(parentState.getDescription(), new OperationStartEvent(parentState.getStartTime()));
        }
        return parentState;
    }

    private void maybeStopUnmanagedThreadOperation() {
        BuildOperationState current = getCurrentBuildOperation();
        if (current instanceof UnmanagedThreadOperation) {
            try {
                listener.finished(current.getDescription(), new OperationFinishEvent(current.getStartTime(), clock.getCurrentTime(), null, null));
            } finally {
                setCurrentBuildOperation(null);
                current.setRunning(false);
            }
        }
    }

    /**
     * Artificially create a running root operation.
     * Main use case is ProjectBuilder, useful for some of our test fixtures too.
     */
    protected void createRunningRootOperation(String displayName) {
        assert getCurrentBuildOperation() == null;
        OperationIdentifier rootBuildOpId = new OperationIdentifier(DefaultBuildOperationIdFactory.ROOT_BUILD_OPERATION_ID_VALUE);
        BuildOperationState operation = new BuildOperationState(BuildOperationDescriptor.displayName(displayName).build(rootBuildOpId, null), clock.getCurrentTime());
        operation.setRunning(true);
        setCurrentBuildOperation(operation);
    }

    private static String formatMultipleFailureMessage(List<GradleException> failures) {
        return failures.stream()
            .map(Throwable::getMessage)
            .collect(Collectors.joining(LINE_SEPARATOR + "AND" + LINE_SEPARATOR));
    }

    @Override
    public void stop() {
        fixedSizePool.stop();
    }

    /**
     * Remembers the operation running on the executing thread at creation time to use
     * it during execution on other threads.
     */
    private class ParentPreservingQueueWorker<O extends BuildOperation> implements BuildOperationQueue.QueueWorker<O> {
        private final BuildOperationState parent;
        private final BuildOperationWorker<? super O> worker;

        private ParentPreservingQueueWorker(BuildOperationWorker<? super O> worker) {
            this.parent = maybeStartUnmanagedThreadOperation(getCurrentBuildOperation());
            this.worker = worker;
        }

        @Override
        public String getDisplayName() {
            return "runnable worker";
        }

        @Override
        public void execute(O buildOperation) {
            DefaultBuildOperationExecutor.this.execute(buildOperation, worker, parent);
        }
    }

    private static class UnmanagedThreadOperation extends BuildOperationState {

        private static final AtomicLong UNMANAGED_THREAD_OPERATION_COUNTER = new AtomicLong(-1);

        private static UnmanagedThreadOperation create(long currentTime) {
            // TODO:pm Move this to WARN level once we fixed maven-publish, see gradle/gradle#1662
            LOGGER.debug("WARNING No operation is currently running in unmanaged thread: {}", Thread.currentThread().getName());
            OperationIdentifier id = new OperationIdentifier(UNMANAGED_THREAD_OPERATION_COUNTER.getAndDecrement());
            String displayName = "Unmanaged thread operation #" + id + " (" + Thread.currentThread().getName() + ')';
            return new UnmanagedThreadOperation(BuildOperationDescriptor.displayName(displayName).build(id, null), currentTime);
        }

        private UnmanagedThreadOperation(BuildOperationDescriptor descriptor, long startTime) {
            super(descriptor, startTime);
        }
    }
}
