package uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Component
import java.util.concurrent.Executor

@Configuration
@EnableAsync
@EnableScheduling
class BackfillAsyncConfig {

  @Bean(name = ["backfillExecutor"])
  fun backfillExecutor(): Executor = ThreadPoolTaskExecutor().apply {
    corePoolSize = 4
    maxPoolSize = 4
    queueCapacity = 8
    setThreadNamePrefix("backfill-")
    initialize()
  }
}

@Component
class BackfillSweepScheduler(private val runner: BackfillRunner) {

  @Scheduled(fixedDelay = 60_000)
  fun sweep() {
    runner.sweepStaleRuns()
  }
}
