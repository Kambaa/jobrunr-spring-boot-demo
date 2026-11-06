package tr.com.yusufgunduz.jobrunrdemo;

import org.jobrunr.jobs.annotations.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DemoJobService {

  private static final Logger LOGGER = LoggerFactory.getLogger(DemoJobService.class);

  @Job(name = "Demo job: %0")
  public void run(String message) {
    LOGGER.info("Background job executed: {}", message);
  }
}
