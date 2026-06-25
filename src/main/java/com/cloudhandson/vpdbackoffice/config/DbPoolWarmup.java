package com.cloudhandson.vpdbackoffice.config;

import com.cloudhandson.vpdbackoffice.service.PermissionService;
import com.cloudhandson.vpdbackoffice.service.ProtectedObjectService;
import java.sql.Connection;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class DbPoolWarmup {

  private static final Logger log = LoggerFactory.getLogger(DbPoolWarmup.class);
  private final DataSource dataSource;
  private final ProtectedObjectService protectedObjectService;
  private final PermissionService permissionService;

  public DbPoolWarmup(
      DataSource dataSource,
      ProtectedObjectService protectedObjectService,
      PermissionService permissionService
  ) {
    this.dataSource = dataSource;
    this.protectedObjectService = protectedObjectService;
    this.permissionService = permissionService;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void warmup() {
    try (Connection ignored = dataSource.getConnection()) {
      log.info("Backoffice DB pool warmed up");
      warmupBackofficeCatalog();
    } catch (Exception exception) {
      log.warn("Backoffice DB pool warm-up failed: {}", exception.getMessage());
    }
  }

  private void warmupBackofficeCatalog() {
    long started = System.nanoTime();
    var objects = protectedObjectService.findEnabled();
    objects.forEach(object -> protectedObjectService.findColumns(object.objectId()));
    protectedObjectService.findDatabaseObjects();
    permissionService.findRoles();
    permissionService.findPermissionViews();
    log.info("Backoffice DB catalog cache warmed up in {}ms", (System.nanoTime() - started) / 1_000_000);
  }
}
