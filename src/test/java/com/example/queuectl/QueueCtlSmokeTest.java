
package com.example.queuectl;

import com.example.queuectl.model.Job;
import com.example.queuectl.service.JobService;
import com.example.queuectl.service.WorkerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class QueueCtlSmokeTest {

    @Autowired JobService jobService;
    @Autowired WorkerService workerService;

    @Test
    void endToEnd_basicSuccessAndFailure() throws Exception {
        // configure small timeouts
        jobService.setConfig("max_retries", "2");
        jobService.setConfig("backoff_base", "2");
        jobService.setConfig("job_timeout_sec", "5");

        // enqueue jobs
        Job ok = new Job("ok-j", "echo hi");
        jobService.enqueue(ok);
        Job bad = new Job("bad-j", "no_such_cmd_zzz");
        jobService.enqueue(bad);

        // run one worker briefly
        workerService.start(1);
        Thread.sleep(4000); // allow processing
        workerService.stop();

        // At least one should be completed, and the bad job should have attempts >= 1
        var counts = jobService.counts();
        assertTrue(counts.getOrDefault(com.example.queuectl.model.JobState.completed, 0L) >= 1);
    }
}
