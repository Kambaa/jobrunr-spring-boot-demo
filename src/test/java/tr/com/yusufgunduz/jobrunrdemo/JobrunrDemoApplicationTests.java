package tr.com.yusufgunduz.jobrunrdemo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.UUID;
import org.jobrunr.jobs.states.StateName;
import org.jobrunr.storage.StorageProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:jobrunr-test;DATABASE_TO_LOWER=TRUE;DB_CLOSE_ON_EXIT=FALSE",
    "org.jobrunr.dashboard.enabled=false"
})
@AutoConfigureMockMvc
class JobrunrDemoApplicationTests {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private StorageProvider storageProvider;

  @Test
  void submittedJobIsPersistedAndExecuted() throws Exception {
    String response = mockMvc.perform(post("/jobs").param("message", "Integration test"))
        .andExpect(status().isAccepted())
        .andReturn().getResponse().getContentAsString();
    UUID jobId = UUID.fromString(objectMapper.readTree(response).get("jobId").asText());

    assertThat(storageProvider.getJobById(jobId).getJobDetails().getJobParameters().get(0)
        .getObject()).isEqualTo("Integration test");
    await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
        assertThat(storageProvider.getJobById(jobId).getState()).isEqualTo(StateName.SUCCEEDED));
  }

  @Test
  void missingMessageUsesDefault() throws Exception {
    String response = mockMvc.perform(post("/jobs"))
        .andExpect(status().isAccepted())
        .andReturn().getResponse().getContentAsString();
    UUID jobId = UUID.fromString(objectMapper.readTree(response).get("jobId").asText());

    assertThat(storageProvider.getJobById(jobId).getJobDetails().getJobParameters().get(0)
        .getObject()).isEqualTo("Hello from JobRunr!");
  }

  @Test
  void invalidMessagesAreRejected() throws Exception {
    mockMvc.perform(post("/jobs").param("message", "   "))
        .andExpect(status().isBadRequest());
    mockMvc.perform(post("/jobs").param("message", "x".repeat(501)))
        .andExpect(status().isBadRequest());
  }

}
