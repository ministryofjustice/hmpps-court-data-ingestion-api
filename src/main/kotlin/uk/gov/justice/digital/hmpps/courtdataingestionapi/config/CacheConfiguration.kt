package uk.gov.justice.digital.hmpps.courtdataingestionapi.config

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import java.util.concurrent.TimeUnit.MINUTES

@Configuration
@EnableCaching
@EnableScheduling
class CacheConfiguration {

  @Bean
  fun cacheManager(): CacheManager = ConcurrentMapCacheManager(PRISON_ROLL)

  @CacheEvict(allEntries = true, cacheNames = [PRISON_ROLL])
  @Scheduled(fixedDelay = 5, timeUnit = MINUTES)
  fun cacheEvict() {
    log.debug("Evicting cached prison rolls")
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(CacheConfiguration::class.java)
    const val PRISON_ROLL: String = "prisonRoll"
  }
}
