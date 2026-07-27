---
# バッチ

[READMEに戻る](../../README.md)

- SpringBatchを実装する場合は「build.gradle」に、次の設定を追加する。

```gradle
	// SpringBatchを使用するための設定
	implementation 'org.springframework.boot:spring-boot-starter-batch'
	testImplementation 'org.springframework.batch:spring-batch-test'
```

- application.propertiesに、次の設定を追加する。

```
# SpringBatch(必要なDBテーブルはh2で毎回作成、アプリ起動時にJobを自動実行しない)
spring.batch.jdbc.initialize-schema=always
spring.batch.job.enabled=false
```

- バッチの構成は、次の通り。

```
src/main/java/com/sb/sblib/
 ├─ batch/
 │   └─ BatchConfig.java
 ├─ tasklet/
 │   ├─ TaskletA.java
 │   └─ TaskletB.java
 └─ runner/
      └─ BatchRunner.java
```

- バッチの本体であるタスクレットは、次のように実装する。

```java
package com.example.demo.batch;

import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

@Component
public class TaskletA implements Tasklet {
    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        System.out.println(">>> Running TaskletA");
        return RepeatStatus.FINISHED;
    }
}
```

```java
package com.example.demo.batch;

import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

@Component
public class TaskletB implements Tasklet {
    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        System.out.println(">>> Running TaskletB");
        return RepeatStatus.FINISHED;
    }
}
```

- バッチのConfigクラスは、次のように記述する。


```java
package com.example.demo.batch;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@EnableBatchProcessing
public class BatchConfig {

    @Bean
    public Step stepA(JobRepository jobRepository, PlatformTransactionManager txManager, TaskletA taskletA) {
        return new StepBuilder("stepA", jobRepository)
                .tasklet(taskletA, txManager)
                .build();
    }

    @Bean
    public Step stepB(JobRepository jobRepository, PlatformTransactionManager txManager, TaskletB taskletB) {
        return new StepBuilder("stepB", jobRepository)
                .tasklet(taskletB, txManager)
                .build();
    }

    @Bean
    public Job jobA(JobRepository jobRepository, Step stepA) {
        return new JobBuilder("jobA", jobRepository)
                .start(stepA)
                .build();
    }

    @Bean
    public Job jobB(JobRepository jobRepository, Step stepB) {
        return new JobBuilder("jobB", jobRepository)
                .start(stepB)
                .build();
    }
}
```

- Webアプリのコントローラからバッチを起動する場合は、次のように記述する。

```java
package com.example.demo.controller;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/batch")
public class BatchController {

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private Job jobA;

    @Autowired
    private Job jobB;

    @PostMapping("/{jobName}")
    public String launchJob(@PathVariable String jobName) throws Exception {
        JobParametersBuilder params = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis());

        if ("jobA".equals(jobName)) {
            jobLauncher.run(jobA, params.toJobParameters());
        } else if ("jobB".equals(jobName)) {
            jobLauncher.run(jobB, params.toJobParameters());
        } else {
            return "Unknown job: " + jobName;
        }
        return "Launched " + jobName;
    }
}
```

- コマンドラインからバッチを起動する場合は、次のように記述する。

```java
package com.example.demo.runner;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class BatchRunner implements ApplicationRunner {

    private final JobLauncher jobLauncher;
    private final Job jobA;
    private final Job jobB;

    public BatchRunner(JobLauncher jobLauncher, Job jobA, Job jobB) {
        this.jobLauncher = jobLauncher;
        this.jobA = jobA;
        this.jobB = jobB;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (args.containsOption("job")) {
            String jobName = args.getOptionValues("job").get(0);
            JobParametersBuilder params = new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis());

            if ("jobA".equals(jobName)) {
                jobLauncher.run(jobA, params.toJobParameters());
            } else if ("jobB".equals(jobName)) {
                jobLauncher.run(jobB, params.toJobParameters());
            } else {
                System.out.println("Unknown job: " + jobName);
            }
        }
    }
}
```

- バッチ専用のSpringBootプロジェクトを別途作成すると、Gradleコマンドやバッチ(シェル)経由でSpringBatchを実行できる。
  - Webアプリからバッチ起動する場合は、上記の方法で行う。(Web起動とバッチ起動の同居はできない)
  - バッチやシェルからSpringBatchを起動したい場合は、プロジェクトを分離する必要がある。
- バッチ専用のSpringBootプロジェクトでは、application.propertiesで次のように明示的にバッチ起動の設定を記述する。

```
[application.properties]

# Webアプリは明示的に起動しない設定(none)とし、バッチジョブは有効(true)にする
spring.main.web-application-type=none
spring.batch.job.enabled=true
```

- 上記で紹介したWebからSpringBatchを起動するケースの設定と見比べると、違いが分かりやすい。

```
[application.properties]

# アプリ起動時にJobを自動実行しない
spring.batch.job.enabled=false
```

- Gradleコマンド経由の場合、次のように記述することでバッチを起動できる。

```
# Linuxの場合は ./gradlew となる
gradlew bootRun --args='--job=jobA'
gradlew bootRun --args='--job=jobB'
```

- バッチでの起動方法は、次の通り。

```sh
java -jar [Gradleビルドで得られた実行可能jar] --job=jobA
java -jar [Gradleビルドで得られた実行可能jar] --job=jobB
```
