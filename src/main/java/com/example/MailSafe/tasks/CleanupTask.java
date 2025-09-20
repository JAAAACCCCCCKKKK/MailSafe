package com.example.MailSafe.tasks;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

@Component
public class CleanupTask {
    private static final Logger logger = LoggerFactory.getLogger(CleanupTask.class);
    private final JdbcTemplate template;

    public CleanupTask(JdbcTemplate template) {
        this.template = template;
    }

    // 每 10 分钟执行一次
    @Scheduled(fixedRate = 600000)
    public void cleanup() {
        try {
            template.update("DELETE FROM mail_tasks WHERE status IN ('PENDING', 'FAILED') AND created_at < ?",
                    LocalDateTime.now().minusHours(1));
            logger.info("Cleaned up old PENDING and FAILED mail tasks");

            template.update("DELETE FROM attachments WHERE mail_task_id NOT IN (SELECT id FROM mail_tasks)");
            logger.info("Cleaned up old attachments");
        } catch (Exception e) {
            logger.error("Error during cleanup: " + e.getMessage(), e);
        }
    }
}

