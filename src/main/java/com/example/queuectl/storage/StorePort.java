
package com.example.queuectl.storage;

import com.example.queuectl.model.Job;
import java.util.List;
import java.util.Map;

public interface StorePort {
    Map<String,Object> loadConfig();
    void saveConfig(Map<String,Object> cfg);

    List<Job> loadJobs();
    void saveJobs(List<Job> jobs);

    List<Job> loadDlq();
    void saveDlq(List<Job> jobs);
}
