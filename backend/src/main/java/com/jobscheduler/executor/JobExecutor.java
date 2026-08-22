package com.jobscheduler.executor;

import com.jobscheduler.entity.Job;

/**
 * Contract that every executor must implement.
 *
 * JobPoller looks up the executor by matching the queue name to
 * {@link #queueName()} and then calls {@link #execute(Job)}.
 *
 * Throwing any exception from execute() causes the job to be failed/retried.
 */
public interface JobExecutor {

    /** The exact queue name this executor handles (case-insensitive match). */
    String queueName();

    /** Perform the actual work. Throw to signal failure. */
    void execute(Job job) throws Exception;
}
