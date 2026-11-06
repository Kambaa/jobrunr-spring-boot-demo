package tr.com.yusufgunduz.jobrunrdemo;

import java.util.Map;
import org.jobrunr.jobs.JobId;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class JobController {

  private final JobScheduler jobScheduler;
  private final DemoJobService demoJobService;

  public JobController(JobScheduler jobScheduler, DemoJobService demoJobService) {
    this.jobScheduler = jobScheduler;
    this.demoJobService = demoJobService;
  }

  @PostMapping("/jobs")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public Map<String, String> enqueue(
      @RequestParam(defaultValue = "Hello from JobRunr!") String message) {
    if (message.isBlank() || message.length() > 500) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "Message must contain between 1 and 500 characters");
    }
    JobId jobId = jobScheduler.enqueue(() -> demoJobService.run(message));
    return Map.of("jobId", jobId.toString());
  }
}
