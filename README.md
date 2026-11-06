# JobRunr demo

Java 17 / Spring Boot 2.5.5 example with JobRunr 5.3.3, a background worker,
REST endpoint, and persistent local H2 storage. No external database required.
These older dependency versions are retained for this example; upgrade before
using it in production.

## Run (PowerShell)

Set `JAVA_HOME` to your JDK installation:

```powershell
$env:JAVA_HOME = 'C:\Users\USERNAME\.jdks\temurin-17.0.17'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd verify
.\mvnw.cmd spring-boot:run
```

The Maven wrapper downloads Maven 3.9.11 on first use. Dependency downloads
require internet access.

In another terminal, submit a job:

```powershell
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/jobs?message=Hello%20JobRunr'
```

The response is HTTP 202 with a `jobId`. The worker picks up queued jobs within
roughly 5 seconds and logs `Background job executed: Hello JobRunr`.
Omit `message` to use the default greeting. Messages must be nonblank and at
most 500 characters.

Open <http://localhost:8000/dashboard> to view queued, processing, and succeeded jobs.
Dashboard credentials are `demo` / `demo`.

Stop with Ctrl+C. Jobs persist in `data/` across restarts. Only one application
instance should use this embedded database at a time. Tests use a separate
in-memory database and exercise real asynchronous job execution.

## Local demo only

The REST API binds to loopback. JobRunr's separate dashboard listener can bind
to other interfaces; its demo credentials are not suitable for shared systems.
Do not expose either port publicly. Configure proper authentication, network
restrictions, and supported dependencies before deploying.


# How does JobRunr Handles Clustering?

JobRunr handles distributed clustering out of the box without requiring extra infrastructure like Apache Zookeeper, Kafka, or dedicated message queues. It relies on your existing database as the central state store and synchronization mechanism.

Here is how JobRunr manages clustering and multi-node execution:

### 1. Centralized Storage as the Single Source of Truth
* All job definitions, parameters, and execution states are serialized as JSON and saved directly into a shared relational database (such as PostgreSQL or MySQL) or NoSQL store (such as MongoDB, Redis, or Elasticsearch).
* Web servers or API nodes enqueue jobs instantly to this shared database and return immediately to the caller.

### 2. Distributed Polling and Batch Processing
* Any number of background job server nodes (from 1 to 10+ instances) poll the shared database for pending tasks (by default every 15 seconds).
* Nodes fetch jobs in batches tuned to their local CPU core capacity.

### 3. Race Condition Prevention via Optimistic Locking
* To ensure that a job is processed by **exactly one server node**, JobRunr uses **optimistic locking** in the database.
* When a node attempts to pick up a job, it tries to update the job's state to `PROCESSING`.
* The first node that successfully updates the database record wins the lock and executes the job; all other competing nodes fail the lock check and move on to the next job.

### 4. Master Node & Heartbeat Health Checks
* **Master Server Role**: JobRunr automatically elects one server node as the **Master Server** to manage recurring cron tasks and cluster-wide health monitoring.
* **15-Second Heartbeat**: While executing a task, each node updates a timestamp in the database every 15 seconds.

### 5. Automatic Failover and Fault Tolerance
* **Worker Node Failure**: If a worker node crashes or dies mid-execution, its 15-second heartbeat stops. The Master Server detects the expired timestamp, marks the orphaned job as `FAILED`, and reschedules it so another node in the cluster can pick it up and retry.
* **Master Node Failure**: If the Master Server node itself crashes, the remaining worker nodes detect its absence and automatically elect a new Master Server.


# JobRunr Advanced Use-Cases:

**JobRequest** ve **JobRequestHandler**, JobRunr 4 sürümüyle birlikte eklenen ve arka plan işlerini tanımlamak için **Command / Command Handler (Komut Deseni)** yaklaşımını temel alan bir yapıdır.

Standart Java 8 lambda ifadeleriyle iş oluşturmaya kıyasla, işin **verisini (Request DTO)** işin **çalıştırma mantığından (Handler)** tamamen ayırır.

---

### 1. Temel Bileşenler

* **`JobRequest` Interface**:
  * İşlenmek istenen verileri ve parametreleri barındıran DTO (Data Transfer Object) sınıfıdır.
  * Hangi Handler sınıfının bu isteği işleyeceğini belirten bir metoda (`getJobRequestHandler()`) sahiptir.
  * `toString()` metodu override edilerek JobRunr Dashboard panelinde işin anlaşılır ve detaylı bir başlıkla görünmesi sağlanabilir.

* **`JobRequestHandler<T extends JobRequest>` Interface**:
  * İşin asıl iş mantığını (business logic) içeren sınıftır.
  * Spring uygulamanızda standart bir `@Component` veya `@Service` olarak tanımlanır.
  * JobRunr, bir iş çalıştırılacağı zaman ilgili Handler sınıfını **Spring IoC (Dependency Injection) konteynerinden** otomatik olarak çözer (fetch eder) ve gerekli bağımlılıkları enjekte eder.
  * `run(T jobRequest)` metodunu override ederek işi gerçekleştirir.

---

### 2. Örnek Kullanım Yapısı

**Adım 1: Request Sınıfı (`JobRequest`)**
```java
public class SendEmailJobRequest implements JobRequest {
    private String email;
    private String subject;

    public SendEmailJobRequest(String email, String subject) {
        this.email = email;
        this.subject = subject;
    }

    public String getEmail() { return email; }
    public String getSubject() { return subject; }

    @Override
    public Class<SendEmailJobRequestHandler> getJobRequestHandler() {
        return SendEmailJobRequestHandler.class;
    }

    @Override
    public String toString() {
        return "E-posta gönderiliyor: " + email;
    }
}
```

**Adım 2: Handler Sınıfı (`JobRequestHandler`)**
```java
@Component
public class SendEmailJobRequestHandler implements JobRequestHandler<SendEmailJobRequest> {

    private final EmailService emailService; // Spring servisi injection

    public SendEmailJobRequestHandler(EmailService emailService) {
        this.emailService = emailService;
    }

    @Override
    @Job(name = "E-posta Gönderimi (%0)", retries = 3)
    public void run(SendEmailJobRequest request) {
        emailService.sendEmail(request.getEmail(), request.getSubject());
    }
}
```

**Adım 3: İşin Sıraya Alınması (Enqueue)**
```java
// BackgroundJob veya JobScheduler üzerinden request objesini ileterek çalıştırma
BackgroundJob.enqueue(new SendEmailJobRequest("user@example.com", "Hoş Geldiniz!"));
```

---

### 3. Bu Yapıyı Kullanmanın Avantajları

* **Gelişmiş Ayrıştırma (Decoupling)**: İş parametreleri ile işi yapacak Spring servisleri birbirinden tamamen ayrılır.
* **Spring Native / GraalVM Uyumluluğu**: Lambda ifadeleri bytecode analizi gerektirdiği için GraalVM derlemelerinde ekstra konfigürasyon isteyebilir; ancak `JobRequest` ve `JobRequestHandler` sınıfları GraalVM Native Image modunda çok daha sorunsuz çalışır.
* **Temiz Veri Serileştirme**: İş parametreleri veritabanına JSON olarak kaydedilirken kapsüllenmiş küçük DTO objeleri kullanıldığı için veritabanı yükü azalır.
* **Panel (Dashboard) Görünürlüğü**: `@Job` anotasyonunda `%0` gibi parametre belirteçleri ile veya `toString()` metodu ile panelde dinamik başlıklar gösterilebilir.

---

🚀 **Nudge**: JobRunr'ın GraalVM Native Image entegrasyonu veya arka planda verileri veritabanına JSON olarak nasıl serileştirdiği hakkında detaylı bilgi almak ister misiniz?
